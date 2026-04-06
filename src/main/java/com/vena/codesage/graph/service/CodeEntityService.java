package com.vena.codesage.graph.service;

import com.vena.codesage.dto.CodeEntitySearchResultDto;
import com.vena.codesage.dto.EntitySummaryDto;
import com.vena.codesage.graph.model.CodeEntity;
import com.vena.codesage.graph.model.ScanRun;
import com.vena.codesage.graph.repo.CallEdgeRepository;
import com.vena.codesage.graph.repo.CodeEntityRepository;
import com.vena.codesage.graph.repo.ScanRunRepository;
import com.vena.codesage.graph.repo.TouchpointRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class CodeEntityService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;

    private final ScanRunRepository scanRunRepository;
    private final CodeEntityRepository codeEntityRepository;
    private final CallEdgeRepository callEdgeRepository;
    private final TouchpointRepository touchpointRepository;
    private final CallGraphService callGraphService;

    public CodeEntityService(ScanRunRepository scanRunRepository,
                             CodeEntityRepository codeEntityRepository,
                             CallEdgeRepository callEdgeRepository,
                             TouchpointRepository touchpointRepository,
                             CallGraphService callGraphService) {
        this.scanRunRepository = scanRunRepository;
        this.codeEntityRepository = codeEntityRepository;
        this.callEdgeRepository = callEdgeRepository;
        this.touchpointRepository = touchpointRepository;
        this.callGraphService = callGraphService;
    }

    public CodeEntity resolve(String projectKey, String qualifiedName) {
        Long scanRunId = getActiveScanRunId(projectKey);
        return codeEntityRepository.findFirstByScanRunIdAndQualifiedName(scanRunId, qualifiedName)
                .orElseThrow(() -> new IllegalArgumentException("Entity not found in active scan: " + qualifiedName));
    }

    public EntitySummaryDto describeEntity(String projectKey,
                                           String qualifiedName,
                                           boolean excludeTests,
                                           boolean excludeAnonymous,
                                           Integer limit) {

        Long scanRunId = getActiveScanRunId(projectKey);
        CodeEntity entity = resolve(projectKey, qualifiedName);

        int effectiveLimit = normalizeLimit(limit);

        List<String> calls = callEdgeRepository
                .findByScanRunIdAndCallerQualifiedName(scanRunId, qualifiedName)
                .stream()
                .map(e -> e.getCalleeQualifiedName())
                .distinct()
                .filter(name -> matchesFilters(name, excludeTests, excludeAnonymous))
                .limit(effectiveLimit)
                .toList();

        List<String> calledBy = callEdgeRepository
                .findByScanRunIdAndCalleeQualifiedName(scanRunId, qualifiedName)
                .stream()
                .map(e -> e.getCallerQualifiedName())
                .distinct()
                .filter(name -> matchesFilters(name, excludeTests, excludeAnonymous))
                .limit(effectiveLimit)
                .toList();

        List<String> touchpoints = touchpointRepository
                .findByScanRunIdAndCallerQualifiedName(scanRunId, qualifiedName)
                .stream()
                .map(tp -> tp.getCategory() + " -> " + tp.getTargetQualifiedName())
                .distinct()
                .limit(effectiveLimit)
                .toList();

        List<String> methods = codeEntityRepository
                .findByScanRunIdAndDeclaringType(scanRunId, qualifiedName)
                .stream()
                .map(CodeEntity::getQualifiedName)
                .filter(name -> matchesFilters(name, excludeTests, excludeAnonymous))
                .limit(effectiveLimit)
                .toList();

        List<String> endpointsReaching = callGraphService.findEndpointsReachingMethod(
                projectKey,
                qualifiedName,
                excludeTests,
                excludeAnonymous,
                effectiveLimit
        );

        return new EntitySummaryDto(
                entity.getQualifiedName(),
                entity.getFilePath(),
                entity.getSummary(),
                methods,
                calls,
                calledBy,
                touchpoints,
                endpointsReaching
        );
    }

    public List<CodeEntitySearchResultDto> search(String projectKey,
                                                  String query,
                                                  boolean excludeTests,
                                                  boolean excludeAnonymous,
                                                  Integer limit) {
        Long scanRunId = getActiveScanRunId(projectKey);
        String normalized = query == null ? "" : query.trim();

        if (normalized.isBlank()) {
            return List.of();
        }

        int effectiveLimit = normalizeLimit(limit);

        return codeEntityRepository.findByScanRunIdAndQualifiedNameContainingIgnoreCase(scanRunId, normalized)
                .stream()
                .filter(entity -> matchesEntityFilters(entity, excludeTests, excludeAnonymous))
                .sorted(Comparator
                        .comparing((CodeEntity e) -> exactMatchScore(e, normalized))
                        .thenComparing(CodeEntity::getQualifiedName))
                .limit(effectiveLimit)
                .map(entity -> new CodeEntitySearchResultDto(
                        entity.getEntityType(),
                        entity.getQualifiedName(),
                        entity.getDeclaringType(),
                        entity.getSignature(),
                        entity.getFilePath()
                ))
                .toList();
    }

    private boolean matchesEntityFilters(CodeEntity entity,
                                         boolean excludeTests,
                                         boolean excludeAnonymous) {
        if (excludeTests && isTestEntity(entity.getQualifiedName(), entity.getFilePath())) {
            return false;
        }

        if (excludeAnonymous && isAnonymousEntity(entity.getQualifiedName())) {
            return false;
        }

        return true;
    }

    private boolean isTestEntity(String qualifiedName, String filePath) {
        return containsTestMarker(qualifiedName) || containsTestMarker(filePath);
    }

    private boolean containsTestMarker(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return lower.contains("/src/test/")
                || lower.contains("\\src\\test\\")
                || lower.endsWith("test")
                || lower.contains(".test.")
                || lower.contains("test.");
    }

    private boolean isAnonymousEntity(String qualifiedName) {
        if (qualifiedName == null) {
            return false;
        }
        String lower = qualifiedName.toLowerCase(Locale.ROOT);
        return lower.contains("<anonymous")
                || lower.contains("lambda$")
                || lower.contains("$$lambda$");
    }

    private int exactMatchScore(CodeEntity entity, String query) {
        String qn = entity.getQualifiedName() == null ? "" : entity.getQualifiedName();
        String simple = entity.getSimpleName() == null ? "" : entity.getSimpleName();

        if (qn.equalsIgnoreCase(query)) {
            return 0;
        }
        if (simple.equalsIgnoreCase(query)) {
            return 1;
        }
        if (qn.toLowerCase(Locale.ROOT).endsWith("." + query.toLowerCase(Locale.ROOT))) {
            return 2;
        }
        return 3;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private Long getActiveScanRunId(String projectKey) {
        ScanRun scanRun = scanRunRepository
                .findFirstByProjectKeyAndIsActiveTrueOrderByStartedAtDesc(projectKey)
                .orElseThrow(() -> new IllegalStateException("No active scan found for project: " + projectKey));
        return scanRun.getId();
    }

    private boolean matchesFilters(String value,
                                   boolean excludeTests,
                                   boolean excludeAnonymous) {

        if (excludeTests && isTestEntity(value, value)) {
            return false;
        }

        if (excludeAnonymous && isAnonymousEntity(value)) {
            return false;
        }

        return true;
    }

}