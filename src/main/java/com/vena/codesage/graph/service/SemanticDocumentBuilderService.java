package com.vena.codesage.graph.service;

import com.vena.codesage.dto.SemanticBuildResponse;
import com.vena.codesage.dto.SemanticDocumentDto;
import com.vena.codesage.dto.SemanticSearchResultDto;
import com.vena.codesage.graph.model.CallEdge;
import com.vena.codesage.graph.model.CodeEntity;
import com.vena.codesage.graph.model.EndpointMapping;
import com.vena.codesage.graph.model.ScanRun;
import com.vena.codesage.graph.model.SemanticDocument;
import com.vena.codesage.graph.model.Touchpoint;
import com.vena.codesage.graph.repo.CallEdgeRepository;
import com.vena.codesage.graph.repo.CodeEntityRepository;
import com.vena.codesage.graph.repo.EndpointMappingRepository;
import com.vena.codesage.graph.repo.ScanRunRepository;
import com.vena.codesage.graph.repo.SemanticDocumentRepository;
import com.vena.codesage.graph.repo.TouchpointRepository;
import com.vena.codesage.graph.service.ranking.RankedDocument;
import com.vena.codesage.graph.service.ranking.SemanticRanker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class SemanticDocumentBuilderService {

    private static final Logger log = LoggerFactory.getLogger(SemanticDocumentBuilderService.class);

    private static final int CHUNK_SIZE = 750;
    private static final int PROGRESS_LOG_INTERVAL = 5_000;

    private final ScanRunRepository scanRunRepository;
    private final CodeEntityRepository codeEntityRepository;
    private final SemanticDocumentRepository semanticDocumentRepository;
    private final CallEdgeRepository callEdgeRepository;
    private final EndpointMappingRepository endpointMappingRepository;
    private final TouchpointRepository touchpointRepository;
    private final ExecutorService semanticBuildExecutor;
    private final TransactionTemplate transactionTemplate;
    private final SemanticRanker semanticRanker;
    private final SemanticBuildSupport semanticBuildSupport = new SemanticBuildSupport();

    public SemanticDocumentBuilderService(ScanRunRepository scanRunRepository,
                                          CodeEntityRepository codeEntityRepository,
                                          SemanticDocumentRepository semanticDocumentRepository,
                                          CallEdgeRepository callEdgeRepository,
                                          EndpointMappingRepository endpointMappingRepository,
                                          TouchpointRepository touchpointRepository,
                                          @Qualifier("semanticBuildExecutor") ExecutorService semanticBuildExecutor,
                                          TransactionTemplate transactionTemplate,
                                          SemanticRanker semanticRanker) {
        this.scanRunRepository = scanRunRepository;
        this.codeEntityRepository = codeEntityRepository;
        this.semanticDocumentRepository = semanticDocumentRepository;
        this.callEdgeRepository = callEdgeRepository;
        this.endpointMappingRepository = endpointMappingRepository;
        this.touchpointRepository = touchpointRepository;
        this.semanticBuildExecutor = semanticBuildExecutor;
        this.transactionTemplate = transactionTemplate;
        this.semanticRanker = semanticRanker;
    }

    @CacheEvict(value = "semanticSearch", allEntries = true)
    public SemanticBuildResponse buildForActiveScan(String projectKey) {
        long startedAt = System.nanoTime();
        ScanRun scanRun = activeScan(projectKey);
        Long scanRunId = scanRun.getId();

        transactionTemplate.executeWithoutResult(status -> semanticDocumentRepository.deleteByScanRunId(scanRunId));

        List<CodeEntity> entities = codeEntityRepository.findByScanRunId(scanRunId)
                .stream()
                .collect(Collectors.toMap(
                        this::entityIdentityKey,
                        entity -> entity,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();

        int totalSelected = entities.size();
        AtomicInteger processed = new AtomicInteger(0);

        log.info("Semantic build started for scanRunId={} projectKey={} totalSelected={}",
                scanRunId, projectKey, totalSelected);

        List<CallEdge> callEdges = callEdgeRepository.findByScanRunId(scanRunId);
        List<EndpointMapping> endpoints = endpointMappingRepository.findByScanRunId(scanRunId);
        List<Touchpoint> touchpoints = touchpointRepository.findByScanRunId(scanRunId);

        log.info("Semantic build prefetched graph data for scanRunId={}: callEdges={}, endpoints={}, touchpoints={}",
                scanRunId, callEdges.size(), endpoints.size(), touchpoints.size());

        long snapshotStartedAt = System.nanoTime();
        SemanticBuildSupport.GraphSnapshot snapshot = semanticBuildSupport.buildSnapshot(entities, callEdges, endpoints, touchpoints);
        log.info("Semantic build snapshot ready for scanRunId={} in {} ms",
                scanRunId, elapsedMillis(snapshotStartedAt));

        List<List<CodeEntity>> chunks = partition(entities, CHUNK_SIZE);
        log.info("Semantic build partitioned scanRunId={} into {} chunks of size about {}",
                scanRunId, chunks.size(), CHUNK_SIZE);

        List<Future<List<SemanticDocument>>> futures = new ArrayList<>();
        for (List<CodeEntity> chunk : chunks) {
            futures.add(semanticBuildExecutor.submit(new ChunkBuilder(scanRunId, chunk, snapshot)));
        }

        List<SemanticDocument> allDocs = new ArrayList<>();
        for (Future<List<SemanticDocument>> future : futures) {
            List<SemanticDocument> docs;
            try {
                docs = dedupeSemanticDocuments(future.get());
            } catch (Exception e) {
                log.error("Semantic chunk build failed for scanRunId={}", scanRunId, e);
                throw new IllegalStateException("Semantic chunk build failed", e);
            }

            if (docs.isEmpty()) {
                continue;
            }

            allDocs.addAll(docs);
            int current = processed.addAndGet(docs.size());
            if (shouldLogProgress(current, docs.size(), totalSelected)) {
                double percent = totalSelected == 0 ? 100.0 : (current * 100.0 / totalSelected);
                log.info("Semantic build progress scanRunId={} processed={} / {} ({}%)",
                        scanRunId, current, totalSelected, String.format(Locale.ROOT, "%.1f", percent));
            }
        }

        List<SemanticDocument> uniqueDocs = dedupeSemanticDocuments(allDocs);
        log.info("Semantic build deduped scanRunId={} rawDocs={} uniqueDocs={}",
                scanRunId, allDocs.size(), uniqueDocs.size());

        for (List<SemanticDocument> docChunk : partitionSemanticDocs(uniqueDocs, CHUNK_SIZE)) {
            transactionTemplate.executeWithoutResult(status -> semanticDocumentRepository.saveAll(docChunk));
        }

        log.info("Semantic build finished for scanRunId={} projectKey={} totalSelected={} totalProcessed={} totalSaved={} durationMs={}",
                scanRunId, projectKey, totalSelected, processed.get(), uniqueDocs.size(), elapsedMillis(startedAt));

        return new SemanticBuildResponse(scanRunId, projectKey, uniqueDocs.size());
    }

    public SemanticDocumentDto findForActiveScan(String projectKey, String qualifiedName) {
        ScanRun scanRun = activeScan(projectKey);

        SemanticDocument doc = semanticDocumentRepository
                .findFirstByScanRunIdAndEntityQualifiedName(scanRun.getId(), qualifiedName)
                .orElseThrow(() -> new IllegalArgumentException("Semantic document not found for: " + qualifiedName));

        return new SemanticDocumentDto(
                doc.getEntityQualifiedName(),
                doc.getEntityType(),
                doc.getDocType(),
                doc.getFilePath(),
                doc.getContent()
        );
    }

    public List<SemanticSearchResultDto> searchActiveScan(String projectKey,
                                                          String query,
                                                          Integer limit,
                                                          boolean excludeTests,
                                                          boolean excludeAnonymous,
                                                          String entityType) {
        return searchActiveScan(projectKey, query, limit, excludeTests, excludeAnonymous, entityType, false);
    }

    @Cacheable(
            value = "semanticSearch",
            key = "#projectKey + '|' + #query + '|' + #limit + '|' + #excludeTests + '|' + #excludeAnonymous + '|' + #entityType + '|' + #debug",
            condition = "!#debug"
    )
    public List<SemanticSearchResultDto> searchActiveScan(String projectKey,
                                                          String query,
                                                          Integer limit,
                                                          boolean excludeTests,
                                                          boolean excludeAnonymous,
                                                          String entityType,
                                                          boolean debug) {
        ScanRun scanRun = activeScan(projectKey);

        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        int effectiveLimit = normalizeLimit(limit);
        String normalizedEntityType = entityType == null ? null : entityType.trim().toUpperCase(Locale.ROOT);

        return semanticDocumentRepository.findByScanRunId(scanRun.getId())
                .stream()
                .collect(Collectors.toMap(
                        doc -> safe(doc.getEntityQualifiedName()) + "|" + safe(doc.getFilePath()) + "|" + safe(doc.getDocType()),
                        doc -> doc,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .filter(doc -> matchesSearchFilters(doc, excludeTests, excludeAnonymous, normalizedEntityType))
                .map(doc -> toSearchResult(doc, normalizedQuery, debug))
                .filter(result -> result.score() > 0)
                .sorted((left, right) -> {
                    int byScore = Integer.compare(right.score(), left.score());
                    if (byScore != 0) {
                        return byScore;
                    }
                    return left.entityQualifiedName().compareToIgnoreCase(right.entityQualifiedName());
                })
                .limit(effectiveLimit)
                .toList();
    }

    private ScanRun activeScan(String projectKey) {
        return scanRunRepository
                .findFirstByProjectKeyAndIsActiveTrueOrderByStartedAtDesc(projectKey)
                .orElseThrow(() -> new IllegalStateException("No active scan found for project: " + projectKey));
    }

    private boolean matchesSearchFilters(SemanticDocument doc,
                                         boolean excludeTests,
                                         boolean excludeAnonymous,
                                         String entityType) {
        if (entityType != null && !entityType.isBlank() && !entityType.equalsIgnoreCase(safe(doc.getEntityType()))) {
            return false;
        }

        String qualifiedName = safe(doc.getEntityQualifiedName());
        String filePath = safe(doc.getFilePath());

        if (excludeTests && isTestLike(qualifiedName, filePath)) {
            return false;
        }

        return !excludeAnonymous || !isAnonymousLike(qualifiedName);
    }

    private SemanticSearchResultDto toSearchResult(SemanticDocument doc, String query, boolean debugEnabled) {
        RankedDocument ranked = semanticRanker.rank(doc, query, debugEnabled);
        return new SemanticSearchResultDto(
                doc.getEntityQualifiedName(),
                doc.getEntityType(),
                doc.getDocType(),
                doc.getFilePath(),
                buildPreview(doc.getContent(), query),
                ranked.score(),
                ranked.debug()
        );
    }

    private String buildPreview(String content, String query) {
        if (content == null || content.isBlank()) {
            return "";
        }

        String normalizedContent = content;
        String lowerContent = content.toLowerCase(Locale.ROOT);
        String lowerQuery = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        int index = lowerQuery.isBlank() ? -1 : lowerContent.indexOf(lowerQuery);

        if (index < 0 && !lowerQuery.isBlank()) {
            for (String token : lowerQuery.split("\\s+")) {
                if (token.isBlank()) {
                    continue;
                }
                index = lowerContent.indexOf(token);
                if (index >= 0) {
                    break;
                }
            }
        }

        if (index < 0) {
            return truncate(content.replaceAll("\\s+", " ").trim(), 220);
        }

        int start = Math.max(0, index - 80);
        int end = Math.min(normalizedContent.length(), index + Math.max(lowerQuery.length(), 20) + 140);
        String preview = normalizedContent.substring(start, end).replaceAll("\\s+", " ").trim();

        if (start > 0) {
            preview = "..." + preview;
        }
        if (end < normalizedContent.length()) {
            preview = preview + "...";
        }
        return preview;
    }

    private boolean shouldLogProgress(int current, int delta, int totalSelected) {
        return current == delta
                || current == totalSelected
                || current / PROGRESS_LOG_INTERVAL > (current - delta) / PROGRESS_LOG_INTERVAL;
    }

    private List<List<CodeEntity>> partition(List<CodeEntity> entities, int chunkSize) {
        List<List<CodeEntity>> chunks = new ArrayList<>();
        for (int i = 0; i < entities.size(); i += chunkSize) {
            chunks.add(entities.subList(i, Math.min(i + chunkSize, entities.size())));
        }
        return chunks;
    }

    private String entityIdentityKey(CodeEntity entity) {
        return safe(entity.getEntityType())
                + "|" + safe(entity.getQualifiedName())
                + "|" + safe(entity.getSignature())
                + "|" + safe(entity.getFilePath());
    }

    private boolean isTestLike(String qualifiedName, String filePath) {
        return containsTestMarker(qualifiedName) || containsTestMarker(filePath);
    }

    private boolean containsTestMarker(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("/src/test/")
                || lower.contains("\\src\\test\\")
                || lower.endsWith("test")
                || lower.contains(".test.")
                || lower.contains("test.");
    }

    private boolean isAnonymousLike(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return false;
        }
        String lower = qualifiedName.toLowerCase(Locale.ROOT);
        return lower.contains("<anonymous")
                || lower.contains("lambda$")
                || lower.contains("$$lambda$");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 20;
        }
        return Math.min(limit, 100);
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value == null ? "" : value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }

    private List<SemanticDocument> dedupeSemanticDocuments(List<SemanticDocument> docs) {
        return docs.stream()
                .collect(Collectors.toMap(
                        this::semanticDocumentIdentityKey,
                        doc -> doc,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }

    private String semanticDocumentIdentityKey(SemanticDocument doc) {
        return safe(doc.getScanRunId() == null ? null : String.valueOf(doc.getScanRunId()))
                + "|" + safe(doc.getDocType())
                + "|" + safe(doc.getEntityQualifiedName())
                + "|" + safe(doc.getFilePath());
    }

    private List<List<SemanticDocument>> partitionSemanticDocs(List<SemanticDocument> docs, int chunkSize) {
        List<List<SemanticDocument>> chunks = new ArrayList<>();
        for (int i = 0; i < docs.size(); i += chunkSize) {
            chunks.add(docs.subList(i, Math.min(i + chunkSize, docs.size())));
        }
        return chunks;
    }

    private final class ChunkBuilder implements Callable<List<SemanticDocument>> {

        private final Long scanRunId;
        private final List<CodeEntity> chunk;
        private final SemanticBuildSupport.GraphSnapshot snapshot;

        private ChunkBuilder(Long scanRunId,
                             List<CodeEntity> chunk,
                             SemanticBuildSupport.GraphSnapshot snapshot) {
            this.scanRunId = scanRunId;
            this.chunk = chunk;
            this.snapshot = snapshot;
        }

        @Override
        public List<SemanticDocument> call() {
            List<SemanticDocument> docs = new ArrayList<>(chunk.size());
            for (CodeEntity entity : chunk) {
                semanticBuildSupport.buildDocument(scanRunId, entity, snapshot)
                        .ifPresent(docs::add);
            }
            return docs;
        }
    }
}
