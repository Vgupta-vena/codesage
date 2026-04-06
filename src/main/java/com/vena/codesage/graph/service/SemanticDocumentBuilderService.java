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
import com.vena.codesage.graph.service.ranking.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class SemanticDocumentBuilderService {

    private static final Logger log = LoggerFactory.getLogger(SemanticDocumentBuilderService.class);

    private static final int DEFAULT_SECTION_LIMIT = 20;
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
        ScanRun scanRun = scanRunRepository
                .findFirstByProjectKeyAndIsActiveTrueOrderByStartedAtDesc(projectKey)
                .orElseThrow(() -> new IllegalStateException("No active scan found for project: " + projectKey));

        Long scanRunId = scanRun.getId();

        transactionTemplate.executeWithoutResult(status ->
                semanticDocumentRepository.deleteByScanRunId(scanRunId)
        );

        List<CodeEntity> entities = codeEntityRepository.findByScanRunId(scanRunId)
                .stream()
                .collect(Collectors.toMap(
                        this::entityIdentityKey,
                        e -> e,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();

        int totalSelected = entities.size();
        AtomicInteger processed = new AtomicInteger(0);

        log.info(
                "Semantic build started for scanRunId={} projectKey={} totalSelected={}",
                scanRunId,
                projectKey,
                totalSelected
        );

        List<CallEdge> callEdges = callEdgeRepository.findByScanRunId(scanRunId);
        List<EndpointMapping> endpoints = endpointMappingRepository.findByScanRunId(scanRunId);
        List<Touchpoint> touchpoints = touchpointRepository.findByScanRunId(scanRunId);

        log.info(
                "Semantic build prefetched graph data for scanRunId={}: callEdges={}, endpoints={}, touchpoints={}",
                scanRunId,
                callEdges.size(),
                endpoints.size(),
                touchpoints.size()
        );

        GraphSnapshot snapshot = buildSnapshot(entities, callEdges, endpoints, touchpoints);

        List<List<CodeEntity>> chunks = partition(entities, CHUNK_SIZE);

        log.info(
                "Semantic build partitioned scanRunId={} into {} chunks of size about {}",
                scanRunId,
                chunks.size(),
                CHUNK_SIZE
        );

        List<Future<List<SemanticDocument>>> futures = new ArrayList<>();
        for (List<CodeEntity> chunk : chunks) {
            futures.add(semanticBuildExecutor.submit(new ChunkBuilder(scanRunId, chunk, snapshot)));
        }

        List<SemanticDocument> allDocs = new ArrayList<>();

        for (Future<List<SemanticDocument>> future : futures) {
            List<SemanticDocument> docs;
            try {
                docs = future.get();
            } catch (Exception e) {
                log.error("Semantic chunk build failed for scanRunId={}", scanRunId, e);
                throw new IllegalStateException("Semantic chunk build failed", e);
            }

            docs = dedupeSemanticDocuments(docs);

            if (docs.isEmpty()) {
                continue;
            }

            allDocs.addAll(docs);

            int current = processed.addAndGet(docs.size());

            if (shouldLogProgress(current, docs.size(), totalSelected)) {
                double percent = totalSelected == 0 ? 100.0 : (current * 100.0 / totalSelected);
                log.info(
                        "Semantic build progress scanRunId={} processed={} / {} ({}%)",
                        scanRunId,
                        current,
                        totalSelected,
                        String.format(Locale.ROOT, "%.1f", percent)
                );
            }
        }

        List<SemanticDocument> uniqueDocs = dedupeSemanticDocuments(allDocs);

        log.info(
                "Semantic build deduped scanRunId={} rawDocs={} uniqueDocs={}",
                scanRunId,
                allDocs.size(),
                uniqueDocs.size()
        );

        List<List<SemanticDocument>> docChunks = partitionSemanticDocs(uniqueDocs, CHUNK_SIZE);
        for (List<SemanticDocument> docChunk : docChunks) {
            transactionTemplate.executeWithoutResult(status ->
                    semanticDocumentRepository.saveAll(docChunk)
            );
        }

        log.info(
                "Semantic build finished for scanRunId={} projectKey={} totalSelected={} totalProcessed={} totalSaved={}",
                scanRunId,
                projectKey,
                totalSelected,
                processed.get(),
                uniqueDocs.size()
        );

        return new SemanticBuildResponse(scanRunId, projectKey, uniqueDocs.size());
    }

    public SemanticDocumentDto findForActiveScan(String projectKey, String qualifiedName) {
        ScanRun scanRun = scanRunRepository
                .findFirstByProjectKeyAndIsActiveTrueOrderByStartedAtDesc(projectKey)
                .orElseThrow(() -> new IllegalStateException("No active scan found for project: " + projectKey));

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
        ScanRun scanRun = scanRunRepository
                .findFirstByProjectKeyAndIsActiveTrueOrderByStartedAtDesc(projectKey)
                .orElseThrow(() -> new IllegalStateException("No active scan found for project: " + projectKey));

        String q = query == null ? "" : query.trim();
        if (q.isBlank()) {
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
                .map(doc -> toSearchResult(doc, q, debug))
                .filter(result -> result.score() > 0)
                .sorted((a, b) -> {
                    int byScore = Integer.compare(b.score(), a.score());
                    if (byScore != 0) {
                        return byScore;
                    }
                    return a.entityQualifiedName().compareToIgnoreCase(b.entityQualifiedName());
                })
                .limit(effectiveLimit)
                .toList();
    }

    private boolean matchesSearchFilters(SemanticDocument doc,
                                         boolean excludeTests,
                                         boolean excludeAnonymous,
                                         String entityType) {
        if (entityType != null && !entityType.isBlank()) {
            if (!entityType.equalsIgnoreCase(safe(doc.getEntityType()))) {
                return false;
            }
        }

        String qn = safe(doc.getEntityQualifiedName());
        String filePath = safe(doc.getFilePath());

        if (excludeTests && isTestLike(qn, filePath)) {
            return false;
        }

        if (excludeAnonymous && isAnonymousLike(qn)) {
            return false;
        }

        return true;
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

        int idx = lowerQuery.isBlank() ? -1 : lowerContent.indexOf(lowerQuery);

        if (idx < 0 && !lowerQuery.isBlank()) {
            for (String token : lowerQuery.split("\\s+")) {
                if (token.isBlank()) {
                    continue;
                }
                idx = lowerContent.indexOf(token);
                if (idx >= 0) {
                    break;
                }
            }
        }

        if (idx < 0) {
            return truncate(content.replaceAll("\\s+", " ").trim(), 220);
        }

        int start = Math.max(0, idx - 80);
        int end = Math.min(normalizedContent.length(), idx + Math.max(lowerQuery.length(), 20) + 140);

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

    private GraphSnapshot buildSnapshot(List<CodeEntity> entities,
                                        List<CallEdge> callEdges,
                                        List<EndpointMapping> endpoints,
                                        List<Touchpoint> touchpoints) {

        Map<String, LinkedHashSet<String>> callsByCaller = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> calledByByCallee = new LinkedHashMap<>();

        for (CallEdge edge : callEdges) {
            if (edge.getCallerQualifiedName() != null
                    && edge.getCalleeQualifiedName() != null
                    && isUsableQualifiedName(edge.getCallerQualifiedName(), null)
                    && isUsableQualifiedName(edge.getCalleeQualifiedName(), null)) {

                callsByCaller
                        .computeIfAbsent(edge.getCallerQualifiedName(), k -> new LinkedHashSet<>())
                        .add(edge.getCalleeQualifiedName());

                calledByByCallee
                        .computeIfAbsent(edge.getCalleeQualifiedName(), k -> new LinkedHashSet<>())
                        .add(edge.getCallerQualifiedName());
            }
        }

        Map<String, LinkedHashSet<String>> endpointsByMethod = new LinkedHashMap<>();
        for (EndpointMapping endpoint : endpoints) {
            if (endpoint.getMethodQualifiedName() != null
                    && isUsableQualifiedName(endpoint.getMethodQualifiedName(), endpoint.getFilePath())) {
                endpointsByMethod
                        .computeIfAbsent(endpoint.getMethodQualifiedName(), k -> new LinkedHashSet<>())
                        .add(formatEndpoint(endpoint));
            }
        }

        Map<String, LinkedHashSet<String>> touchpointsByCaller = new LinkedHashMap<>();
        for (Touchpoint tp : touchpoints) {
            if (tp.getCallerQualifiedName() != null
                    && isUsableQualifiedName(tp.getCallerQualifiedName(), tp.getFilePath())) {
                touchpointsByCaller
                        .computeIfAbsent(tp.getCallerQualifiedName(), k -> new LinkedHashSet<>())
                        .add(tp.getCategory() + " -> " + safe(tp.getTargetQualifiedName()));
            }
        }

        Map<String, LinkedHashSet<String>> methodsByDeclaringType = new LinkedHashMap<>();
        for (CodeEntity entity : entities) {
            if (entity.getDeclaringType() != null
                    && !entity.getDeclaringType().isBlank()
                    && entity.getQualifiedName() != null
                    && !entity.getQualifiedName().isBlank()
                    && "METHOD".equalsIgnoreCase(entity.getEntityType())
                    && isUsableQualifiedName(entity.getQualifiedName(), entity.getFilePath())) {
                methodsByDeclaringType
                        .computeIfAbsent(entity.getDeclaringType(), k -> new LinkedHashSet<>())
                        .add(entity.getQualifiedName());
            }
        }

        return new GraphSnapshot(
                callsByCaller,
                calledByByCallee,
                endpointsByMethod,
                touchpointsByCaller,
                methodsByDeclaringType
        );
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
                + "|"
                + safe(entity.getQualifiedName())
                + "|"
                + safe(entity.getSignature())
                + "|"
                + safe(entity.getFilePath());
    }

    private String buildContent(CodeEntity entity,
                                List<String> methods,
                                List<String> calls,
                                List<String> calledBy,
                                List<String> endpointsReaching,
                                List<String> directTouchpoints,
                                List<String> externalLikeCallees,
                                List<String> persistenceLikeCallees) {
        StringBuilder sb = new StringBuilder();

        line(sb, "Qualified Name: " + safe(entity.getQualifiedName()));
        line(sb, "Entity Type: " + safe(entity.getEntityType()));
        line(sb, "Declaring Type: " + safe(entity.getDeclaringType()));
        line(sb, "Package: " + safe(entity.getPackageName()));
        line(sb, "Signature: " + safe(entity.getSignature()));
        line(sb, "File Path: " + safe(entity.getFilePath()));
        blank(sb);

        appendSection(sb, "Methods", methods);
        appendSection(sb, "Called By", calledBy);
        appendSection(sb, "Calls", calls);
        appendSection(sb, "Endpoints Reaching", endpointsReaching);
        appendSection(sb, "Direct Touchpoints", directTouchpoints);
        appendSection(sb, "External-Like Callees", externalLikeCallees);
        appendSection(sb, "Persistence-Like Callees", persistenceLikeCallees);

        line(sb, "Summary:");
        line(sb, buildNaturalSummary(
                entity,
                methods,
                calls,
                calledBy,
                endpointsReaching,
                directTouchpoints,
                externalLikeCallees,
                persistenceLikeCallees
        ));

        return sb.toString().trim();
    }

    private String buildNaturalSummary(CodeEntity entity,
                                       List<String> methods,
                                       List<String> calls,
                                       List<String> calledBy,
                                       List<String> endpointsReaching,
                                       List<String> directTouchpoints,
                                       List<String> externalLikeCallees,
                                       List<String> persistenceLikeCallees) {
        StringBuilder s = new StringBuilder();

        s.append(safe(entity.getQualifiedName()));
        s.append(" is a ");
        s.append(lowerOrUnknown(entity.getEntityType()));

        if (entity.getDeclaringType() != null && !entity.getDeclaringType().isBlank()) {
            s.append(" declared in ");
            s.append(entity.getDeclaringType());
        }

        if (!methods.isEmpty()) {
            s.append(". It includes methods such as ");
            s.append(joinPreview(methods, 4));
        }

        if (!calledBy.isEmpty()) {
            s.append(". It is called by ");
            s.append(joinPreview(calledBy, 3));
        }

        if (!endpointsReaching.isEmpty()) {
            s.append(". It is reachable from endpoints such as ");
            s.append(joinPreview(endpointsReaching, 3));
        }

        if (!calls.isEmpty()) {
            s.append(". It directly calls ");
            s.append(joinPreview(calls, 4));
        }

        if (!directTouchpoints.isEmpty()) {
            s.append(". Direct touchpoints include ");
            s.append(joinPreview(directTouchpoints, 3));
        }

        if (!externalLikeCallees.isEmpty()) {
            s.append(". Likely external integration paths include ");
            s.append(joinPreview(externalLikeCallees, 3));
        }

        if (!persistenceLikeCallees.isEmpty()) {
            s.append(". Likely persistence-related paths include ");
            s.append(joinPreview(persistenceLikeCallees, 3));
        }

        return s.toString();
    }

    private List<String> limitedList(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().limit(DEFAULT_SECTION_LIMIT).toList();
    }

    private boolean isExternalLike(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("clientservice")
                || lower.contains("client")
                || lower.contains("resttemplate")
                || lower.contains("webclient")
                || lower.contains("feign")
                || lower.contains("httpclient")
                || lower.contains("auth0")
                || lower.contains("external")
                || lower.contains("integration")
                || lower.contains("connector");
    }

    private boolean isPersistenceLike(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("repository")
                || lower.contains("dao")
                || lower.contains("entitymanager")
                || lower.contains("jdbctemplate")
                || lower.contains("namedparameterjdbc")
                || lower.endsWith(".save")
                || lower.endsWith(".delete")
                || lower.endsWith(".insert")
                || lower.endsWith(".persist")
                || lower.endsWith(".merge")
                || lower.endsWith(".update")
                || lower.endsWith(".findbyid")
                || lower.contains("genericdao");
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

    private boolean isUsableQualifiedName(String qualifiedName, String filePath) {
        return !isTestLike(qualifiedName, filePath) && !isAnonymousLike(qualifiedName);
    }

    private String formatEndpoint(EndpointMapping endpoint) {
        String httpMethod = endpoint.getHttpMethod() == null ? "" : endpoint.getHttpMethod();
        String classPath = endpoint.getClassPath() == null ? "" : endpoint.getClassPath();
        String methodPath = endpoint.getMethodPath() == null ? "" : endpoint.getMethodPath();
        String qualifiedMethod = endpoint.getMethodQualifiedName() == null ? "" : endpoint.getMethodQualifiedName();
        return (httpMethod + " " + classPath + methodPath).trim() + " -> " + qualifiedMethod;
    }

    private void appendSection(StringBuilder sb, String title, List<String> items) {
        line(sb, title + ":");
        if (items == null || items.isEmpty()) {
            line(sb, "- none");
        } else {
            for (String item : items) {
                line(sb, "- " + safe(item));
            }
        }
        blank(sb);
    }

    private String joinPreview(List<String> items, int limit) {
        return items.stream().limit(limit).reduce((a, b) -> a + ", " + b).orElse("none");
    }

    private String lowerOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown entity" : value.toLowerCase(Locale.ROOT);
    }

    private void line(StringBuilder sb, String line) {
        sb.append(line).append(System.lineSeparator());
    }

    private void blank(StringBuilder sb) {
        sb.append(System.lineSeparator());
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
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to hash semantic content", e);
        }
    }

    private List<String> findTransitiveEndpoints(String startMethod,
                                                 Map<String, LinkedHashSet<String>> calledByByCallee,
                                                 Map<String, LinkedHashSet<String>> endpointsByMethod,
                                                 int maxDepth) {
        if (startMethod == null) {
            return List.of();
        }

        LinkedHashSet<String> visited = new LinkedHashSet<>();
        LinkedHashSet<String> endpoints = new LinkedHashSet<>();

        List<String> frontier = List.of(startMethod);
        int depth = 0;

        while (!frontier.isEmpty() && depth < maxDepth) {
            List<String> next = new ArrayList<>();

            for (String current : frontier) {
                LinkedHashSet<String> callers = calledByByCallee.get(current);
                if (callers == null) {
                    continue;
                }

                for (String caller : callers) {
                    if (!visited.add(caller)) {
                        continue;
                    }

                    LinkedHashSet<String> eps = endpointsByMethod.get(caller);
                    if (eps != null) {
                        endpoints.addAll(eps);
                    }

                    next.add(caller);

                    if (endpoints.size() >= DEFAULT_SECTION_LIMIT) {
                        return endpoints.stream().limit(DEFAULT_SECTION_LIMIT).toList();
                    }
                }
            }

            frontier = next;
            depth++;
        }

        return endpoints.stream().limit(DEFAULT_SECTION_LIMIT).toList();
    }

    private record GraphSnapshot(
            Map<String, LinkedHashSet<String>> callsByCaller,
            Map<String, LinkedHashSet<String>> calledByByCallee,
            Map<String, LinkedHashSet<String>> endpointsByMethod,
            Map<String, LinkedHashSet<String>> touchpointsByCaller,
            Map<String, LinkedHashSet<String>> methodsByDeclaringType
    ) {
    }

    private final class ChunkBuilder implements Callable<List<SemanticDocument>> {

        private final Long scanRunId;
        private final List<CodeEntity> chunk;
        private final GraphSnapshot snapshot;

        private ChunkBuilder(Long scanRunId,
                             List<CodeEntity> chunk,
                             GraphSnapshot snapshot) {
            this.scanRunId = scanRunId;
            this.chunk = chunk;
            this.snapshot = snapshot;
        }

        @Override
        public List<SemanticDocument> call() {
            List<SemanticDocument> docs = new ArrayList<>(chunk.size());

            for (CodeEntity entity : chunk) {
                if (!isUsableQualifiedName(entity.getQualifiedName(), entity.getFilePath())) {
                    continue;
                }

                String qn = entity.getQualifiedName();

                List<String> methods = limitedList(snapshot.methodsByDeclaringType().get(qn));

                List<String> calls = new ArrayList<>();
                List<String> calledBy = new ArrayList<>();
                List<String> endpointsReaching = new ArrayList<>();
                List<String> directTouchpoints = new ArrayList<>();

                if ("METHOD".equalsIgnoreCase(entity.getEntityType())) {
                    calls = limitedList(snapshot.callsByCaller().get(qn));
                    calledBy = limitedList(snapshot.calledByByCallee().get(qn));

                    endpointsReaching = findTransitiveEndpoints(
                            qn,
                            snapshot.calledByByCallee(),
                            snapshot.endpointsByMethod(),
                            3
                    );
                    if (endpointsReaching.isEmpty()) {
                        endpointsReaching = limitedList(snapshot.endpointsByMethod().get(qn));
                    }
                    directTouchpoints = limitedList(snapshot.touchpointsByCaller().get(qn));
                } else if (!methods.isEmpty()) {
                    calls = aggregateForMethods(methods, snapshot.callsByCaller());
                    calledBy = aggregateForMethods(methods, snapshot.calledByByCallee());
                    endpointsReaching = aggregateForMethods(methods, snapshot.endpointsByMethod());
                    directTouchpoints = aggregateForMethods(methods, snapshot.touchpointsByCaller());
                }

                if (methods.isEmpty()
                        && calls.isEmpty()
                        && calledBy.isEmpty()
                        && endpointsReaching.isEmpty()
                        && directTouchpoints.isEmpty()) {
                    continue;
                }

                List<String> externalLikeCallees = calls.stream()
                        .filter(SemanticDocumentBuilderService.this::isExternalLike)
                        .limit(DEFAULT_SECTION_LIMIT)
                        .toList();

                List<String> persistenceLikeCallees = calls.stream()
                        .filter(SemanticDocumentBuilderService.this::isPersistenceLike)
                        .limit(DEFAULT_SECTION_LIMIT)
                        .toList();

                String content = buildContent(
                        entity,
                        methods,
                        calls,
                        calledBy,
                        endpointsReaching,
                        directTouchpoints,
                        externalLikeCallees,
                        persistenceLikeCallees
                );

                String docKey = sha256(
                        "SEMANTIC|"
                                + scanRunId + "|"
                                + safe(entity.getEntityType()) + "|"
                                + safe(entity.getQualifiedName()) + "|"
                                + safe(entity.getSignature()) + "|"
                                + safe(entity.getFilePath())
                );

                String contentHash = sha256(content);

                docs.add(SemanticDocument.builder()
                        .scanRunId(scanRunId)
                        .docKey(docKey)
                        .entityQualifiedName(entity.getQualifiedName())
                        .entityType(entity.getEntityType())
                        .docType("GRAPH_SUMMARY")
                        .filePath(entity.getFilePath())
                        .content(content)
                        .contentHash(contentHash)
                        .isActive(true)
                        .build());
            }

            return docs;
        }

        private List<String> aggregateForMethods(List<String> methods,
                                                 Map<String, LinkedHashSet<String>> source) {
            LinkedHashSet<String> values = new LinkedHashSet<>();
            for (String method : methods) {
                LinkedHashSet<String> found = source.get(method);
                if (found != null) {
                    values.addAll(found);
                }
                if (values.size() >= DEFAULT_SECTION_LIMIT) {
                    break;
                }
            }
            return values.stream().limit(DEFAULT_SECTION_LIMIT).toList();
        }
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
                + "|"
                + safe(doc.getDocType())
                + "|"
                + safe(doc.getEntityQualifiedName())
                + "|"
                + safe(doc.getFilePath());
    }

    private List<List<SemanticDocument>> partitionSemanticDocs(List<SemanticDocument> docs, int chunkSize) {
        List<List<SemanticDocument>> chunks = new ArrayList<>();
        for (int i = 0; i < docs.size(); i += chunkSize) {
            chunks.add(docs.subList(i, Math.min(i + chunkSize, docs.size())));
        }
        return chunks;
    }
}