package com.vena.codesage.graph.service;

import com.vena.codesage.dto.CodeEntitySearchResultDto;
import com.vena.codesage.dto.EndpointSearchResultDto;
import com.vena.codesage.dto.SemanticSearchResultDto;
import com.vena.codesage.dto.SmartSearchMode;
import com.vena.codesage.dto.SmartSearchResultDto;
import com.vena.codesage.graph.model.CodeEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SmartSearchService {

    private final EndpointSearchService endpointSearchService;
    private final SemanticSearchService semanticSearchService;
    private final SemanticDocumentBuilderService semanticDocumentBuilderService;
    private final ScanManagerService scanManagerService;

    public SmartSearchService(EndpointSearchService endpointSearchService,
                              SemanticSearchService semanticSearchService,
                              SemanticDocumentBuilderService semanticDocumentBuilderService,
                              ScanManagerService scanManagerService) {
        this.endpointSearchService = endpointSearchService;
        this.semanticSearchService = semanticSearchService;
        this.semanticDocumentBuilderService = semanticDocumentBuilderService;
        this.scanManagerService = scanManagerService;
    }

    public List<SmartSearchResultDto> search(String projectKey,
                                             String query,
                                             Integer limit,
                                             Boolean debug,
                                             SmartSearchMode mode,
                                             Boolean collapse,
                                             String entityType) {
        String normalizedQuery = normalize(query);
        if (normalizedQuery.isBlank()) {
            return List.of();
        }

        int effectiveLimit = normalizeLimit(limit);
        boolean effectiveDebug = debug != null && debug;
        boolean effectiveCollapse = collapse == null || collapse;

        SmartSearchMode effectiveMode = resolveMode(query, mode);

        return switch (effectiveMode) {
            case ENDPOINT -> endpointResults(projectKey, query, effectiveLimit, effectiveCollapse);
            case ENTITY -> entityResults(projectKey, query, effectiveLimit, effectiveCollapse, entityType);
            case SEMANTIC -> semanticResults(projectKey, query, effectiveLimit, effectiveDebug, effectiveCollapse, entityType);
            case AUTO -> autoResults(projectKey, query, effectiveLimit, effectiveDebug, effectiveCollapse, entityType);
        };
    }

    private List<SmartSearchResultDto> autoResults(String projectKey,
                                                   String query,
                                                   int limit,
                                                   boolean debug,
                                                   boolean collapse,
                                                   String entityType) {
        SmartSearchMode autoMode = resolveAutomaticMode(query);
        return switch (autoMode) {
            case ENDPOINT -> endpointResults(projectKey, query, limit, collapse);
            case ENTITY -> entityResults(projectKey, query, limit, collapse, entityType);
            case SEMANTIC -> semanticResults(projectKey, query, limit, debug, collapse, entityType);
            default -> semanticResults(projectKey, query, limit, debug, collapse, entityType);
        };
    }

    private List<SmartSearchResultDto> endpointResults(String projectKey,
                                                       String query,
                                                       int limit,
                                                       boolean collapse) {
        List<EndpointSearchResultDto> raw = endpointSearchService.search(projectKey, query, limit * 10, false);

        List<EndpointSearchResultDto> rankedRaw = looksLikePathQuery(query)
                ? rankRawEndpointPathResults(raw, query)
                : raw;

        List<SmartSearchResultDto> mapped = rankedRaw.stream()
                .map(this::toEndpointResult)
                .toList();

        if (collapse) {
            mapped = collapseByGroup(mapped);
        }

        return mapped.stream().limit(limit).toList();
    }

    private List<SmartSearchResultDto> entityResults(String projectKey,
                                                     String query,
                                                     int limit,
                                                     boolean collapse,
                                                     String entityType) {
        Long scanRunId = scanManagerService.getActiveScan(projectKey).getId();

        List<CodeEntity> raw = semanticSearchService.search(
                scanRunId,
                query,
                limit * 6,
                true,
                true,
                entityType
        );

        List<CodeEntitySearchResultDto> mappedEntities = raw.stream()
                .map(entity -> new CodeEntitySearchResultDto(
                        entity.getEntityType(),
                        entity.getQualifiedName(),
                        entity.getDeclaringType(),
                        entity.getSignature(),
                        entity.getFilePath()
                ))
                .toList();

        if (looksLikeClassQuery(query)) {
            mappedEntities = filterEntityMatchesForClassQuery(mappedEntities, query);
        }

        if (collapse && looksLikeClassQuery(query)) {
            return collapseEntitiesToClassCards(mappedEntities, limit);
        }

        List<SmartSearchResultDto> mapped = mappedEntities.stream()
                .map(this::toEntityResult)
                .toList();

        if (collapse) {
            mapped = collapseByGroup(mapped);
        }

        return mapped.stream().limit(limit).toList();
    }

    private List<CodeEntitySearchResultDto> filterEntityMatchesForClassQuery(List<CodeEntitySearchResultDto> entities,
                                                                             String query) {
        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);

        List<CodeEntitySearchResultDto> filtered = entities.stream()
                .filter(entity -> {
                    String qualifiedName = safe(entity.qualifiedName());
                    String declaringType = safe(entity.declaringType());
                    String declaringTypeShort = extractLastSegment(declaringType);

                    return declaringTypeShort.equals(normalizedQuery)
                            || declaringType.equals(normalizedQuery)
                            || qualifiedName.contains("." + normalizedQuery + ".")
                            || qualifiedName.contains(normalizedQuery);
                })
                .toList();

        return filtered.isEmpty() ? entities : filtered;
    }

    private List<SmartSearchResultDto> collapseEntitiesToClassCards(List<CodeEntitySearchResultDto> entities, int limit) {
        Map<String, List<CodeEntitySearchResultDto>> byDeclaringType = entities.stream()
                .collect(Collectors.groupingBy(
                        entity -> nullSafe(entity.declaringType()).isBlank()
                                ? entity.qualifiedName()
                                : entity.declaringType(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<SmartSearchResultDto> results = new ArrayList<>();

        for (Map.Entry<String, List<CodeEntitySearchResultDto>> entry : byDeclaringType.entrySet()) {
            String declaringType = entry.getKey();
            List<CodeEntitySearchResultDto> members = entry.getValue();

            CodeEntitySearchResultDto representative = members.get(0);

            String topMethods = members.stream()
                    .map(CodeEntitySearchResultDto::qualifiedName)
                    .map(this::extractMethodName)
                    .filter(name -> !name.isBlank())
                    .distinct()
                    .limit(3)
                    .collect(Collectors.joining(", "));

            String preview = topMethods.isBlank()
                    ? "Representative entity match"
                    : "Top methods: " + topMethods;

            results.add(new SmartSearchResultDto(
                    "ENTITY",
                    declaringType,
                    "CLASS_GROUP | " + members.size() + " matches",
                    representative.filePath(),
                    preview,
                    members.size(),
                    declaringType
            ));
        }

        return results.stream().limit(limit).toList();
    }

    private List<SmartSearchResultDto> semanticResults(String projectKey,
                                                       String query,
                                                       int limit,
                                                       boolean debug,
                                                       boolean collapse,
                                                       String entityType) {
        List<SemanticSearchResultDto> raw = semanticDocumentBuilderService.searchActiveScan(
                projectKey,
                query,
                limit * 3,
                true,
                true,
                entityType,
                debug
        );

        List<SmartSearchResultDto> mapped = raw.stream()
                .map(this::toSemanticResult)
                .toList();

        if (collapse) {
            mapped = collapseByGroup(mapped);
        }

        return mapped.stream().limit(limit).toList();
    }

    private List<SmartSearchResultDto> collapseByGroup(List<SmartSearchResultDto> results) {
        Map<String, SmartSearchResultDto> bestByGroup = new LinkedHashMap<>();

         for (SmartSearchResultDto result : results) {
            String key = result.groupKey() == null || result.groupKey().isBlank()
                    ? result.title()
                    : result.groupKey();

            SmartSearchResultDto existing = bestByGroup.get(key);
            if (existing == null) {
                bestByGroup.put(key, result);
                continue;
            }

            int currentScore = result.score() == null ? 0 : result.score();
            int existingScore = existing.score() == null ? 0 : existing.score();

            if (currentScore > existingScore) {
                bestByGroup.put(key, result);
            }
        }

        return new ArrayList<>(bestByGroup.values());
    }

    private SmartSearchResultDto toEndpointResult(EndpointSearchResultDto endpoint) {
        return new SmartSearchResultDto(
                "ENDPOINT",
                endpoint.methodQualifiedName(),
                endpoint.httpMethod() + " " + endpoint.path(),
                endpoint.filePath(),
                endpoint.unresolvedPath() ? "Endpoint mapping with unresolved path" : "Resolved endpoint mapping",
                endpoint.score(),
                endpoint.methodQualifiedName()
        );
    }

    private SmartSearchResultDto toEntityResult(CodeEntitySearchResultDto entity) {
        return new SmartSearchResultDto(
                "ENTITY",
                entity.qualifiedName(),
                entity.entityType() + " | " + nullSafe(entity.signature()),
                entity.filePath(),
                nullSafe(entity.declaringType()),
                null,
                nullSafe(entity.declaringType())
        );
    }

    private SmartSearchResultDto toSemanticResult(SemanticSearchResultDto semantic) {
        return new SmartSearchResultDto(
                "SEMANTIC",
                semantic.entityQualifiedName(),
                semantic.entityType() + " | " + semantic.docType(),
                semantic.filePath(),
                semantic.preview(),
                semantic.score(),
                semantic.entityQualifiedName()
        );
    }

    private SmartSearchMode resolveMode(String query, SmartSearchMode requestedMode) {
        if (requestedMode != null && requestedMode != SmartSearchMode.AUTO) {
            return requestedMode;
        }
        return resolveAutomaticMode(query);
    }

    private SmartSearchMode resolveAutomaticMode(String query) {
        if (looksLikePathQuery(query) || looksLikeEndpointIntent(query)) {
            return SmartSearchMode.ENDPOINT;
        }

        if (looksLikeClassQuery(query) || looksLikeQualifiedSymbol(query)) {
            return SmartSearchMode.ENTITY;
        }

        if (looksLikeEntityIntent(query)) {
            return SmartSearchMode.ENTITY;
        }

        return SmartSearchMode.SEMANTIC;
    }

    private boolean looksLikeEndpointIntent(String query) {
        String lower = query.toLowerCase(Locale.ROOT);

        return lower.contains(" endpoint")
                || lower.contains(" api")
                || lower.contains(" route")
                || lower.contains(" path")
                || lower.contains(" get ")
                || lower.contains(" post ")
                || lower.contains(" put ")
                || lower.contains(" delete ");
    }

    private boolean looksLikeEntityIntent(String query) {
        return looksLikeQualifiedSymbol(query) || looksLikeClassQuery(query);
    }

    private boolean looksLikeClassQuery(String query) {
        if (query == null || query.isBlank()) {
            return false;
        }

        if (query.contains("/") || query.contains("{") || query.contains("}") || query.contains(" ")) {
            return false;
        }

        return !query.equals(query.toLowerCase(Locale.ROOT));
    }

    private boolean looksLikePathQuery(String query) {
        return query != null && query.startsWith("/");
    }

    private boolean looksLikeQualifiedSymbol(String query) {
        return query != null && query.chars().filter(ch -> ch == '.').count() >= 2;
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit < 1) {
            return 10;
        }
        return Math.min(limit, 50);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String extractMethodName(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return "";
        }
        int index = qualifiedName.lastIndexOf('.');
        if (index < 0 || index == qualifiedName.length() - 1) {
            return qualifiedName;
        }
        return qualifiedName.substring(index + 1);
    }

    private String extractLastSegment(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        int index = value.lastIndexOf('.');
        if (index < 0 || index == value.length() - 1) {
            return value.toLowerCase(Locale.ROOT);
        }
        return value.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    private String safe(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private List<SmartSearchResultDto> rankPathQueryResults(List<SmartSearchResultDto> results, String query) {
        String normalizedQuery = normalizePath(query);

        List<SmartSearchResultDto> exact = new ArrayList<>();
        List<SmartSearchResultDto> children = new ArrayList<>();
        List<SmartSearchResultDto> related = new ArrayList<>();
        List<SmartSearchResultDto> others = new ArrayList<>();

        for (SmartSearchResultDto result : results) {
            String path = extractPathFromSubtitle(result.subtitle());

            if (path.equals(normalizedQuery)) {
                exact.add(result);
            } else if (path.startsWith(normalizedQuery + "/")) {
                children.add(result);
            } else if (path.contains(normalizedQuery) || normalizedQuery.contains(path)) {
                related.add(result);
            } else {
                others.add(result);
            }
        }

        Comparator<SmartSearchResultDto> byScoreDesc = (left, right) ->
                Integer.compare(
                        right.score() == null ? 0 : right.score(),
                        left.score() == null ? 0 : left.score()
                );

        exact.sort(byScoreDesc);
        children.sort(byScoreDesc);
        related.sort(byScoreDesc);
        others.sort(byScoreDesc);

        List<SmartSearchResultDto> ranked = new ArrayList<>(results.size());
        ranked.addAll(exact);
        ranked.addAll(children);
        ranked.addAll(related);
        ranked.addAll(others);

        return ranked;
    }

    private String extractPathFromSubtitle(String subtitle) {
        if (subtitle == null || subtitle.isBlank()) {
            return "";
        }

        String trimmed = subtitle.trim();
        int pathStart = trimmed.indexOf('/');

        if (pathStart < 0) {
            return "";
        }

        return normalizePath(trimmed.substring(pathStart));
    }

    private List<EndpointSearchResultDto> rankRawEndpointPathResults(List<EndpointSearchResultDto> results, String query) {
        String normalizedQuery = normalizePath(query);

        List<EndpointSearchResultDto> exact = new ArrayList<>();
        List<EndpointSearchResultDto> children = new ArrayList<>();
        List<EndpointSearchResultDto> related = new ArrayList<>();
        List<EndpointSearchResultDto> others = new ArrayList<>();

        for (EndpointSearchResultDto result : results) {
            String path = normalizePath(result.path());

            if (path.equals(normalizedQuery)) {
                exact.add(result);
            } else if (path.startsWith(normalizedQuery + "/")) {
                children.add(result);
            } else if (path.contains(normalizedQuery) || normalizedQuery.contains(path)) {
                related.add(result);
            } else {
                others.add(result);
            }
        }

        Comparator<EndpointSearchResultDto> byScoreDesc = (left, right) ->
                Integer.compare(right.score(), left.score());

        exact.sort(byScoreDesc);
        children.sort(byScoreDesc);
        related.sort(byScoreDesc);
        others.sort(byScoreDesc);

        List<EndpointSearchResultDto> ranked = new ArrayList<>(results.size());
        ranked.addAll(exact);
        ranked.addAll(children);
        ranked.addAll(related);
        ranked.addAll(others);

        return ranked;
    }

    private String normalizePath(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);

        int firstSlash = normalized.indexOf('/');
        if (firstSlash >= 0) {
            normalized = normalized.substring(firstSlash);
        }

        while (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }
}