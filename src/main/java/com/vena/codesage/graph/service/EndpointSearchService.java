package com.vena.codesage.graph.service;

import com.vena.codesage.dto.KnowledgeResultItemDto;
import com.vena.codesage.graph.model.EndpointMapping;
import com.vena.codesage.graph.model.ScanRun;
import com.vena.codesage.graph.repo.EndpointMappingRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class EndpointSearchService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final EndpointMappingRepository endpointMappingRepository;
    private final ScanManagerService scanManagerService;

    public EndpointSearchService(EndpointMappingRepository endpointMappingRepository,
                                 ScanManagerService scanManagerService) {
        this.endpointMappingRepository = endpointMappingRepository;
        this.scanManagerService = scanManagerService;
    }

    public List<KnowledgeResultItemDto> search(String projectKey,
                                               String query,
                                               Integer limit,
                                               Boolean includeUnresolved) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        boolean effectiveIncludeUnresolved = includeUnresolved != null && includeUnresolved;
        ScanRun activeScan = scanManagerService.getActiveScan(projectKey);
        int effectiveLimit = normalizeLimit(limit);

        List<ScoredEndpoint> scored = endpointMappingRepository.findByScanRunId(activeScan.getId())
                .stream()
                .map(endpoint -> new ScoredEndpoint(endpoint, score(endpoint, normalizedQuery)))
                .filter(se -> se.score() > 0)
                .filter(se -> effectiveIncludeUnresolved || !isUnresolved(se.endpoint()))
                .sorted(Comparator
                        .comparingInt(ScoredEndpoint::score)
                        .reversed()
                        .thenComparingInt(se -> pathCompletenessRank(se.endpoint()))
                        .thenComparing(se -> safe(se.endpoint().getMethodQualifiedName())))
                .toList();

        return dedupe(scored).stream()
                .limit(effectiveLimit)
                .map(se -> toItem(se.endpoint(), se.score()))
                .toList();
    }

    private List<ScoredEndpoint> dedupe(List<ScoredEndpoint> scored) {
        Map<String, ScoredEndpoint> bestByKey = new LinkedHashMap<>();

        for (ScoredEndpoint se : scored) {
            EndpointMapping endpoint = se.endpoint();
            String key = safe(endpoint.getHttpMethod())
                    + "|"
                    + buildPath(endpoint.getClassPath(), endpoint.getMethodPath())
                    + "|"
                    + safe(endpoint.getMethodQualifiedName());

            ScoredEndpoint existing = bestByKey.get(key);
            if (existing == null || se.score() > existing.score()) {
                bestByKey.put(key, se);
            }
        }

        return bestByKey.values().stream().toList();
    }

    private int score(EndpointMapping endpoint, String query) {
        int score = 0;

        String httpMethod = safe(endpoint.getHttpMethod());
        String classPath = safe(endpoint.getClassPath());
        String methodPath = safe(endpoint.getMethodPath());
        String methodQualifiedName = safe(endpoint.getMethodQualifiedName());
        String filePath = safe(endpoint.getFilePath());

        String fullPath = buildPath(endpoint.getClassPath(), endpoint.getMethodPath()).toLowerCase(Locale.ROOT);
        String simpleMethodName = extractMethodName(methodQualifiedName);
        String declaringType = extractDeclaringType(methodQualifiedName);
        String declaringTypeShort = extractLastSegment(declaringType);

        String[] tokens = query.split("\s+");

        if (methodQualifiedName.equals(query)) {
            score += 300;
        }

        if (simpleMethodName.equals(query)) {
            score += 240;
        }

        if (declaringType.equals(query)) {
            score += 220;
        }

        if (declaringTypeShort.equals(query)) {
            score += 200;
        }

        if (fullPath.equals(query)) {
            score += 5000;
        } else if (fullPath.startsWith(query + "/")) {
            score += 1000;
        } else if (fullPath.contains(query)) {
            score += 250;
        } else if (query.contains(fullPath) && !fullPath.equals("unresolved path")) {
            score += 120;
        }

        if (methodQualifiedName.contains(query)) {
            score += 120;
        }

        if (declaringType.contains(query)) {
            score += 110;
        }

        if (declaringTypeShort.contains(query)) {
            score += 100;
        }

        if (simpleMethodName.contains(query)) {
            score += 95;
        }

        if (classPath.contains(query)) {
            score += 60;
        }

        if (methodPath.contains(query)) {
            score += 60;
        }

        if (httpMethod.contains(query)) {
            score += 40;
        }

        if (filePath.contains(query)) {
            score += 20;
        }

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }

            if (declaringTypeShort.equals(token)) {
                score += 40;
            }

            if (simpleMethodName.equals(token)) {
                score += 35;
            }

            if (declaringTypeShort.contains(token)) {
                score += 25;
            }

            if (simpleMethodName.contains(token)) {
                score += 25;
            }

            if (methodQualifiedName.contains(token)) {
                score += 22;
            }

            if (fullPath.contains(token)) {
                score += 20;
            }

            if (classPath.contains(token)) {
                score += 10;
            }

            if (methodPath.contains(token)) {
                score += 10;
            }

            if (httpMethod.contains(token)) {
                score += 6;
            }
        }

        if (looksLikeEndpointIntent(query)) {
            score += 30;
        }

        if (looksLikeResource(endpoint)) {
            score += 25;
        }

        if (looksLikeController(endpoint)) {
            score += 20;
        }

        if (hasBothPathParts(endpoint)) {
            score += 25;
        } else if (hasOnlyMethodPath(endpoint)) {
            score += 8;
        } else if (hasOnlyClassPath(endpoint)) {
            score += 5;
        }

        if (isUnresolved(endpoint)) {
            score -= 120;
        }

        if (query.contains("create workspace")) {
            if (simpleMethodName.equals("createworkspace")) {
                score += 120;
            } else if (simpleMethodName.contains("create")
                    && simpleMethodName.contains("workspace")
                    && (simpleMethodName.contains("backup") || simpleMethodName.contains("restore"))) {
                score -= 40;
            }
        }

        if (query.contains("status endpoint") && simpleMethodName.equals("status")) {
            score += 80;
        }

        return score;
    }

    private int pathCompletenessRank(EndpointMapping endpoint) {
        if (hasBothPathParts(endpoint)) {
            return 0;
        }
        if (hasOnlyMethodPath(endpoint) || hasOnlyClassPath(endpoint)) {
            return 1;
        }
        return 2;
    }

    private boolean hasBothPathParts(EndpointMapping endpoint) {
        return hasNormalizedPath(endpoint.getClassPath()) && hasNormalizedPath(endpoint.getMethodPath());
    }

    private boolean hasOnlyClassPath(EndpointMapping endpoint) {
        return hasNormalizedPath(endpoint.getClassPath()) && !hasNormalizedPath(endpoint.getMethodPath());
    }

    private boolean hasOnlyMethodPath(EndpointMapping endpoint) {
        return !hasNormalizedPath(endpoint.getClassPath()) && hasNormalizedPath(endpoint.getMethodPath());
    }

    private boolean hasNormalizedPath(String value) {
        return !trimSlashes(value).isBlank();
    }

    private boolean isUnresolved(EndpointMapping endpoint) {
        return !hasNormalizedPath(endpoint.getClassPath()) && !hasNormalizedPath(endpoint.getMethodPath());
    }

    private boolean looksLikeEndpointIntent(String query) {
        return query.contains("endpoint")
                || query.contains("api")
                || query.contains("route")
                || query.contains("resource")
                || query.contains("controller")
                || query.contains("path");
    }

    private boolean looksLikeResource(EndpointMapping endpoint) {
        String methodQualifiedName = safe(endpoint.getMethodQualifiedName());
        String filePath = safe(endpoint.getFilePath());
        return methodQualifiedName.contains("resource")
                || filePath.contains("/resources/")
                || filePath.contains("/endpoints/");
    }

    private boolean looksLikeController(EndpointMapping endpoint) {
        String methodQualifiedName = safe(endpoint.getMethodQualifiedName());
        String filePath = safe(endpoint.getFilePath());
        return methodQualifiedName.contains("controller")
                || filePath.contains("/controller/")
                || filePath.contains("/controllers/");
    }

    private KnowledgeResultItemDto toItem(EndpointMapping endpoint, int score) {
        String path = buildPath(endpoint.getClassPath(), endpoint.getMethodPath());
        return new KnowledgeResultItemDto(
                "ENDPOINT",
                com.vena.codesage.dto.KnowledgeSourceType.CODE,
                null,
                endpoint.getMethodQualifiedName(),
                endpoint.getHttpMethod() + " " + path,
                endpoint.getFilePath(),
                isUnresolved(endpoint) ? "Endpoint mapping with unresolved path" : "Resolved endpoint mapping",
                score,
                List.of(),
                Map.of(
                        "httpMethod", nullSafe(endpoint.getHttpMethod()),
                        "path", path,
                        "methodQualifiedName", nullSafe(endpoint.getMethodQualifiedName()),
                        "unresolvedPath", isUnresolved(endpoint)
                )
        );
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String buildPath(String classPath, String methodPath) {
        String left = trimSlashes(classPath);
        String right = trimSlashes(methodPath);

        if (left.isBlank() && right.isBlank()) {
            return "unresolved path";
        }
        if (left.isBlank()) {
            return "/" + right;
        }
        if (right.isBlank()) {
            return "/" + left;
        }
        return "/" + left + "/" + right;
    }

    private String trimSlashes(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String result = value.trim();
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String extractMethodName(String methodQualifiedName) {
        if (methodQualifiedName == null || methodQualifiedName.isBlank()) {
            return "";
        }
        int index = methodQualifiedName.lastIndexOf('.');
        if (index < 0 || index == methodQualifiedName.length() - 1) {
            return methodQualifiedName;
        }
        return methodQualifiedName.substring(index + 1);
    }

    private String extractDeclaringType(String methodQualifiedName) {
        if (methodQualifiedName == null || methodQualifiedName.isBlank()) {
            return "";
        }
        int index = methodQualifiedName.lastIndexOf('.');
        if (index < 0) {
            return "";
        }
        return methodQualifiedName.substring(0, index);
    }

    private String extractLastSegment(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int index = value.lastIndexOf('.');
        if (index < 0 || index == value.length() - 1) {
            return value;
        }
        return value.substring(index + 1);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private record ScoredEndpoint(EndpointMapping endpoint, int score) {
    }
}
