package com.vena.codesage.graph.service;

import com.vena.codesage.dto.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CodebaseKnowledgeService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final CodeEntityService codeEntityService;
    private final SemanticDocumentBuilderService semanticDocumentBuilderService;
    private final EndpointSearchService endpointSearchService;
    private final ReverseTraversalService reverseTraversalService;

    public CodebaseKnowledgeService(CodeEntityService codeEntityService,
                                    SemanticDocumentBuilderService semanticDocumentBuilderService,
                                    EndpointSearchService endpointSearchService, ReverseTraversalService reverseTraversalService) {
        this.codeEntityService = codeEntityService;
        this.semanticDocumentBuilderService = semanticDocumentBuilderService;
        this.endpointSearchService = endpointSearchService;
        this.reverseTraversalService = reverseTraversalService;
    }

    public KnowledgeResponseDto query(String projectKey,
                                      String query,
                                      Integer limit,
                                      boolean debug,
                                      KnowledgeMode mode,
                                      boolean collapse,
                                      String entityType) {

        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isBlank()) {
            return new KnowledgeResponseDto(
                    projectKey,
                    query,
                    mode == null ? KnowledgeMode.AUTO : mode,
                    "Empty query",
                    List.of()
            );
        }

        int effectiveLimit = normalizeLimit(limit);
        KnowledgeMode resolvedMode = resolveMode(normalizedQuery, mode);

        List<KnowledgeResultItemDto> results = switch (resolvedMode) {
            case ENDPOINT -> endpointResults(projectKey, normalizedQuery, effectiveLimit, collapse);
            case ENTITY -> entityResults(projectKey, normalizedQuery, effectiveLimit, collapse);
            case SEMANTIC -> semanticResults(projectKey, normalizedQuery, effectiveLimit, debug, entityType, collapse);
            case AUTO -> autoResults(projectKey, normalizedQuery, effectiveLimit, debug, entityType, collapse);
        };

        return new KnowledgeResponseDto(
                projectKey,
                query,
                resolvedMode,
                buildSummary(resolvedMode, normalizedQuery, results),
                results
        );
    }

    private List<KnowledgeResultItemDto> autoResults(String projectKey,
                                                     String query,
                                                     int limit,
                                                     boolean debug,
                                                     String entityType,
                                                     boolean collapse) {
        KnowledgeMode autoMode = resolveAutomaticMode(query);

        List<KnowledgeResultItemDto> primary = switch (autoMode) {
            case ENDPOINT -> endpointResults(projectKey, query, limit, collapse);
            case ENTITY -> entityResults(projectKey, query, limit, collapse);
            case SEMANTIC -> semanticResults(projectKey, query, limit, debug, entityType, collapse);
            default -> semanticResults(projectKey, query, limit, debug, entityType, collapse);
        };

        if (!primary.isEmpty()) {
            return primary;
        }

        if (autoMode != KnowledgeMode.SEMANTIC) {
            return semanticResults(projectKey, query, limit, debug, entityType, collapse);
        }

        return List.of();
    }

    private List<KnowledgeResultItemDto> endpointResults(String projectKey,
                                                         String query,
                                                         int limit,
                                                         boolean collapse) {
        List<EndpointSearchResultDto> raw = endpointSearchService.search(projectKey, query, limit * 10, false);

        if (looksLikePathQuery(query)) {
            raw = rankRawEndpointPathResults(raw, query);

            List<KnowledgeResultItemDto> grouped = groupExactPathMatches(raw, query, limit);
            if (!grouped.isEmpty()) {
                return grouped;
            }
        }

        List<KnowledgeResultItemDto> mapped = raw.stream()
                .map(this::toEndpointItem)
                .toList();

        if (collapse) {
            mapped = collapseByIdentity(mapped);
        }

        return mapped.stream().limit(limit).toList();
    }

    private List<KnowledgeResultItemDto> entityResults(String projectKey,
                                                       String query,
                                                       int limit,
                                                       boolean collapse) {
        List<CodeEntitySearchResultDto> raw = codeEntityService.search(
                projectKey,
                query,
                true,
                true,
                limit * 8
        );

        if (looksLikeClassQuery(query)) {
            raw = filterEntityMatchesForClassQuery(raw, query);
        }

        if (collapse && looksLikeClassQuery(query)) {
            return collapseEntityMatchesToClassCards(raw, limit);
        }

        List<KnowledgeResultItemDto> mapped = raw.stream()
                .map(this::toEntityItem)
                .toList();

        if (collapse) {
            mapped = collapseByLocationAndTitle(mapped);
        }

        return mapped.stream().limit(limit).toList();
    }

    private List<KnowledgeResultItemDto> semanticResults(String projectKey,
                                                         String query,
                                                         int limit,
                                                         boolean debug,
                                                         String entityType,
                                                         boolean collapse) {
        List<SemanticSearchResultDto> raw = semanticDocumentBuilderService.searchActiveScan(
                projectKey,
                query,
                limit * 4,
                true,
                true,
                entityType,
                debug
        );

        List<KnowledgeResultItemDto> mapped = raw.stream()
                .map(item -> toSemanticItem(projectKey, item))
                .toList();

        if (collapse) {
            mapped = collapseByIdentity(mapped);
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

    private List<KnowledgeResultItemDto> collapseEntityMatchesToClassCards(List<CodeEntitySearchResultDto> entities,
                                                                           int limit) {
        Map<String, List<CodeEntitySearchResultDto>> byDeclaringType = entities.stream()
                .collect(Collectors.groupingBy(
                        entity -> hasText(entity.declaringType()) ? entity.declaringType() : entity.qualifiedName(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        List<KnowledgeResultItemDto> cards = new ArrayList<>();

        for (Map.Entry<String, List<CodeEntitySearchResultDto>> entry : byDeclaringType.entrySet()) {
            String className = entry.getKey();
            List<CodeEntitySearchResultDto> members = entry.getValue();
            CodeEntitySearchResultDto representative = members.get(0);

            List<String> highlights = members.stream()
                    .map(CodeEntitySearchResultDto::qualifiedName)
                    .map(this::extractMethodName)
                    .filter(this::hasText)
                    .filter(name -> !name.startsWith("<"))
                    .distinct()
                    .limit(5)
                    .toList();

            cards.add(new KnowledgeResultItemDto(
                    "ENTITY",
                    className,
                    "CLASS_GROUP | " + members.size() + " matches",
                    representative.filePath(),
                    "Representative class match",
                    members.size(),
                    highlights
            ));
        }

        return cards.stream().limit(limit).toList();
    }

    private List<KnowledgeResultItemDto> collapseByIdentity(List<KnowledgeResultItemDto> results) {
        Map<String, KnowledgeResultItemDto> bestByKey = new LinkedHashMap<>();

        for (KnowledgeResultItemDto result : results) {
            String key = result.resultType() + "|" + result.title() + "|" + result.subtitle();

            KnowledgeResultItemDto existing = bestByKey.get(key);
            if (existing == null || score(result) > score(existing)) {
                bestByKey.put(key, result);
            }
        }

        return new ArrayList<>(bestByKey.values());
    }

    private List<KnowledgeResultItemDto> collapseByLocationAndTitle(List<KnowledgeResultItemDto> results) {
        Map<String, KnowledgeResultItemDto> bestByKey = new LinkedHashMap<>();

        for (KnowledgeResultItemDto result : results) {
            String key = result.title() + "|" + result.location();

            KnowledgeResultItemDto existing = bestByKey.get(key);
            if (existing == null || score(result) > score(existing)) {
                bestByKey.put(key, result);
            }
        }

        return new ArrayList<>(bestByKey.values());
    }

    private List<EndpointSearchResultDto> rankRawEndpointPathResults(List<EndpointSearchResultDto> results,
                                                                     String query) {
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

        Comparator<EndpointSearchResultDto> byScoreDesc =
                (left, right) -> Integer.compare(right.score(), left.score());

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

    private KnowledgeResultItemDto toEndpointItem(EndpointSearchResultDto endpoint) {
        return new KnowledgeResultItemDto(
                "ENDPOINT",
                endpoint.methodQualifiedName(),
                endpoint.httpMethod() + " " + endpoint.path(),
                endpoint.filePath(),
                endpoint.unresolvedPath() ? "Endpoint mapping with unresolved path" : "Resolved endpoint mapping",
                endpoint.score(),
                List.of()
        );
    }

    private KnowledgeResultItemDto toEntityItem(CodeEntitySearchResultDto entity) {
        return new KnowledgeResultItemDto(
                "ENTITY",
                entity.qualifiedName(),
                entity.entityType() + " | " + nullSafe(entity.signature()),
                entity.filePath(),
                nullSafe(entity.declaringType()),
                null,
                List.of()
        );
    }

    private KnowledgeResultItemDto toSemanticItem(String projectKey, SemanticSearchResultDto semantic) {
        return new KnowledgeResultItemDto(
                "SEMANTIC",
                semantic.entityQualifiedName(),
                semantic.entityType() + " | " + semantic.docType(),
                semantic.filePath(),
                semantic.preview(),
                semantic.score(),
                topUpstreamHighlights(projectKey, semantic.entityQualifiedName())
        );
    }

    private KnowledgeMode resolveMode(String query, KnowledgeMode requestedMode) {
        if (requestedMode != null && requestedMode != KnowledgeMode.AUTO) {
            return requestedMode;
        }
        return resolveAutomaticMode(query);
    }

    private KnowledgeMode resolveAutomaticMode(String query) {
        if (looksLikePathQuery(query) || looksLikeEndpointIntent(query)) {
            return KnowledgeMode.ENDPOINT;
        }

        if (looksLikeClassQuery(query) || looksLikeQualifiedSymbol(query)) {
            return KnowledgeMode.ENTITY;
        }

        return KnowledgeMode.SEMANTIC;
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

    private boolean looksLikeClassQuery(String query) {
        if (!hasText(query)) {
            return false;
        }

        if (query.contains("/") || query.contains("{") || query.contains("}") || query.contains(" ")) {
            return false;
        }

        return !query.equals(query.toLowerCase(Locale.ROOT));
    }

    private boolean looksLikeQualifiedSymbol(String query) {
        return query != null && query.chars().filter(ch -> ch == '.').count() >= 2;
    }

    private boolean looksLikePathQuery(String query) {
        return query != null && query.startsWith("/");
    }

    private String buildSummary(KnowledgeMode mode, String query, List<KnowledgeResultItemDto> results) {
        if (results.isEmpty()) {
            return "No results found for \"" + query + "\".";
        }

        return switch (mode) {
            case ENTITY -> "Found " + results.size() + " entity result(s) for \"" + query + "\".";
            case ENDPOINT -> "Found " + results.size() + " endpoint result(s) for \"" + query + "\".";
            case SEMANTIC -> "Found " + results.size() + " semantic result(s) for \"" + query + "\".";
            case AUTO -> "Found " + results.size() + " result(s) for \"" + query + "\".";
        };
    }

    private int score(KnowledgeResultItemDto item) {
        return item.score() == null ? 0 : item.score();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private String normalizePath(String value) {
        if (!hasText(value)) {
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

    private String extractMethodName(String qualifiedName) {
        if (!hasText(qualifiedName)) {
            return "";
        }

        int index = qualifiedName.lastIndexOf('.');
        if (index < 0 || index == qualifiedName.length() - 1) {
            return qualifiedName;
        }
        return qualifiedName.substring(index + 1);
    }

    private String extractLastSegment(String value) {
        if (!hasText(value)) {
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

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<KnowledgeResultItemDto> groupExactPathMatches(List<EndpointSearchResultDto> endpoints,
                                                               String query,
                                                               int limit) {
        String normalizedQuery = normalizePath(query);

        Map<String, List<EndpointSearchResultDto>> byRoute = endpoints.stream()
                .filter(e -> normalizePath(e.path()).equals(normalizedQuery))
                .collect(Collectors.groupingBy(
                        e -> e.httpMethod() + " " + e.path(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        if (byRoute.isEmpty()) {
            return List.of();
        }

        List<KnowledgeResultItemDto> results = new ArrayList<>();

        for (Map.Entry<String, List<EndpointSearchResultDto>> entry : byRoute.entrySet()) {
            List<EndpointSearchResultDto> members = entry.getValue();
            EndpointSearchResultDto representative = members.get(0);

            List<String> highlights = members.stream()
                    .map(EndpointSearchResultDto::methodQualifiedName)
                    .distinct()
                    .limit(10)
                    .toList();

            results.add(new KnowledgeResultItemDto(
                    "ENDPOINT_GROUP",
                    entry.getKey(),
                    "ROUTE_GROUP | " + members.size() + " mapped methods",
                    representative.filePath(),
                    "Exact route match with multiple mapped handlers",
                    members.stream().mapToInt(EndpointSearchResultDto::score).max().orElse(0),
                    highlights
            ));
        }

        return results.stream().limit(limit).toList();
    }

    private List<String> topUpstreamHighlights(String projectKey, String qualifiedName) {
        try {
            var trace = reverseTraversalService.trace(projectKey, qualifiedName, TraceDirection.UPSTREAM, 2, 10);
            return trace.upstream().stream()
                    .map(node -> node.qualifiedName())
                    .limit(3)
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }
}