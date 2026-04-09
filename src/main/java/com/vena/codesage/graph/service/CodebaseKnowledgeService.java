package com.vena.codesage.graph.service;

import com.vena.codesage.dto.CodeEntitySearchResultDto;
import com.vena.codesage.dto.EndpointSearchResultDto;
import com.vena.codesage.dto.KnowledgeMode;
import com.vena.codesage.dto.KnowledgeResponseDto;
import com.vena.codesage.dto.KnowledgeResultItemDto;
import com.vena.codesage.dto.SemanticSearchResultDto;
import com.vena.codesage.dto.TraceDirection;
import com.vena.codesage.integration.confluence.ConfluenceKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CodebaseKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(CodebaseKnowledgeService.class);

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final CodeEntityService codeEntityService;
    private final SemanticDocumentBuilderService semanticDocumentBuilderService;
    private final EndpointSearchService endpointSearchService;
    private final ReverseTraversalService reverseTraversalService;
    private final Map<KnowledgeMode, Function<QueryContext, List<KnowledgeResultItemDto>>> handlers;
    private final ConfluenceKnowledgeService confluenceKnowledgeService;

    public CodebaseKnowledgeService(CodeEntityService codeEntityService,
                                    SemanticDocumentBuilderService semanticDocumentBuilderService,
                                    EndpointSearchService endpointSearchService,
                                    ReverseTraversalService reverseTraversalService, ConfluenceKnowledgeService confluenceKnowledgeService) {
        this.codeEntityService = codeEntityService;
        this.semanticDocumentBuilderService = semanticDocumentBuilderService;
        this.endpointSearchService = endpointSearchService;
        this.reverseTraversalService = reverseTraversalService;
        this.confluenceKnowledgeService = confluenceKnowledgeService;
        this.handlers = buildHandlers();
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
            log.info("Knowledge query skipped for project={} because query is blank", projectKey);
            return KnowledgeResponseDto.simple(
                    projectKey,
                    query,
                    mode == null ? KnowledgeMode.AUTO : mode,
                    "Empty query",
                    List.of()
            );
        }

        int effectiveLimit = normalizeLimit(limit);
        KnowledgeMode resolvedMode = resolveMode(normalizedQuery, mode);
        QueryContext context = new QueryContext(
                projectKey,
                normalizedQuery,
                effectiveLimit,
                debug,
                collapse,
                entityType
        );

        log.info(
                "Knowledge query start project={} mode={} limit={} collapse={} entityType={} debug={} query={}",
                projectKey,
                resolvedMode,
                effectiveLimit,
                collapse,
                entityType,
                debug,
                normalizedQuery
        );

        long startNanos = System.nanoTime();
        List<KnowledgeResultItemDto> results = execute(resolvedMode, context);
        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        log.info(
                "Knowledge query complete project={} mode={} results={} elapsedMs={}",
                projectKey,
                resolvedMode,
                results.size(),
                elapsedMillis
        );

        return new KnowledgeResponseDto(
                projectKey,
                query,
                resolvedMode,
                buildSummary(resolvedMode, normalizedQuery, results),
                results,
                results.size(),
                extractSources(results),
                elapsedMillis
        );
    }

    private Map<KnowledgeMode, Function<QueryContext, List<KnowledgeResultItemDto>>> buildHandlers() {
        EnumMap<KnowledgeMode, Function<QueryContext, List<KnowledgeResultItemDto>>> map =
                new EnumMap<>(KnowledgeMode.class);

        map.put(KnowledgeMode.AUTO, this::autoResults);
        map.put(KnowledgeMode.ENDPOINT, this::endpointResults);
        map.put(KnowledgeMode.ENTITY, this::entityResults);
        map.put(KnowledgeMode.SEMANTIC, this::semanticResults);

        return Map.copyOf(map);
    }

    private List<KnowledgeResultItemDto> execute(KnowledgeMode mode, QueryContext context) {
        Function<QueryContext, List<KnowledgeResultItemDto>> handler = handlers.get(mode);
        if (handler == null) {
            log.warn("No handler configured for mode={}, falling back to semantic", mode);
            return semanticResults(context);
        }
        return handler.apply(context);
    }

    private List<KnowledgeResultItemDto> autoResults(QueryContext context) {
        KnowledgeMode autoMode = resolveAutomaticMode(context.query());

        log.debug("AUTO mode resolved to {} for query={}", autoMode, context.query());

        List<KnowledgeResultItemDto> primary = switch (autoMode) {
            case ENDPOINT -> endpointResults(context);
            case ENTITY -> entityResults(context);
            case SEMANTIC, AUTO -> semanticResults(context);
        };

        if (!primary.isEmpty()) {
            return primary;
        }

        if (autoMode != KnowledgeMode.SEMANTIC) {
            log.debug("AUTO primary mode {} returned no results, falling back to SEMANTIC", autoMode);
            return semanticResults(context);
        }

        return List.of();
    }

    private List<KnowledgeResultItemDto> endpointResults(QueryContext context) {
        List<EndpointSearchResultDto> raw = endpointSearchService.search(
                context.projectKey(),
                context.query(),
                context.limit() * 10,
                false
        );

        log.debug(
                "Endpoint search returned {} raw results for project={} query={}",
                raw.size(),
                context.projectKey(),
                context.query()
        );

        if (looksLikePathQuery(context.query())) {
            raw = rankRawEndpointPathResults(raw, context.query());

            List<KnowledgeResultItemDto> grouped = groupExactPathMatches(raw, context.query(), context.limit());
            if (!grouped.isEmpty()) {
                log.debug("Endpoint exact path grouping produced {} grouped results", grouped.size());
                return grouped;
            }
        }

        List<KnowledgeResultItemDto> mapped = raw.stream()
                .map(this::toEndpointItem)
                .toList();

        if (context.collapse()) {
            mapped = collapseByIdentity(mapped);
        }

        return mapped.stream()
                .limit(context.limit())
                .toList();
    }

    private List<KnowledgeResultItemDto> entityResults(QueryContext context) {
        List<CodeEntitySearchResultDto> raw = codeEntityService.search(
                context.projectKey(),
                context.query(),
                true,
                true,
                context.limit() * 8
        );

        log.debug(
                "Entity search returned {} raw results for project={} query={}",
                raw.size(),
                context.projectKey(),
                context.query()
        );

        if (looksLikeClassQuery(context.query())) {
            raw = filterEntityMatchesForClassQuery(raw, context.query());
        }

        if (context.collapse() && looksLikeClassQuery(context.query())) {
            return collapseEntityMatchesToClassCards(raw, context.limit());
        }

        List<KnowledgeResultItemDto> mapped = raw.stream()
                .map(this::toEntityItem)
                .toList();

        if (context.collapse()) {
            mapped = collapseByLocationAndTitle(mapped);
        }

        return mapped.stream()
                .limit(context.limit())
                .toList();
    }

    private List<KnowledgeResultItemDto> semanticResults(QueryContext context) {
        List<KnowledgeResultItemDto> codeResults = semanticDocumentBuilderService.searchActiveScan(
                        context.projectKey(),
                        context.query(),
                        context.limit() * 3,
                        true,
                        true,
                        context.entityType(),
                        context.debug()
                ).stream()
                .map(item -> toSemanticItem(context.projectKey(), item))
                .toList();

        List<KnowledgeResultItemDto> confluenceResults = confluenceKnowledgeService.search(
                context.query(),
                Math.max(3, context.limit() / 2)
        );

        log.debug(
                "Semantic aggregation returned codeResults={} confluenceResults={} for project={} query={}",
                codeResults.size(),
                confluenceResults.size(),
                context.projectKey(),
                context.query()
        );

        List<KnowledgeResultItemDto> merged = new ArrayList<>(codeResults.size() + confluenceResults.size());
        merged.addAll(codeResults);
        merged.addAll(confluenceResults);

        if (context.collapse()) {
            merged = collapseByIdentity(merged);
        }

        return rankMergedResults(merged, context.query()).stream()
                .limit(context.limit())
                .toList();
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
            CodeEntitySearchResultDto representative = members.getFirst();

            List<String> highlights = members.stream()
                    .map(CodeEntitySearchResultDto::qualifiedName)
                    .map(this::extractMethodName)
                    .filter(this::hasText)
                    .filter(name -> !name.startsWith("<"))
                    .distinct()
                    .limit(5)
                    .toList();

            cards.add(KnowledgeResultItemDto.codeEntity(
                    className,
                    "CLASS_GROUP | " + members.size() + " matches",
                    representative.filePath(),
                    "Representative class match",
                    members.size(),
                    highlights
            ));
        }

        return cards.stream()
                .limit(limit)
                .toList();
    }

    private List<KnowledgeResultItemDto> collapseByIdentity(List<KnowledgeResultItemDto> results) {
        Map<String, KnowledgeResultItemDto> bestByKey = new LinkedHashMap<>();

        for (KnowledgeResultItemDto result : results) {
            String key = result.sourceType() + "|" + result.sourceId() + "|" + result.resultType() + "|" + result.title() + "|" + result.subtitle();

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
            String key = result.sourceType() + "|" + result.sourceId() + "|" + result.title() + "|" + result.location();

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
        return KnowledgeResultItemDto.endpoint(
                endpoint.methodQualifiedName(),
                endpoint.httpMethod() + " " + endpoint.path(),
                endpoint.filePath(),
                endpoint.unresolvedPath() ? "Endpoint mapping with unresolved path" : "Resolved endpoint mapping",
                endpoint.score(),
                List.of()
        );
    }

    private KnowledgeResultItemDto toEntityItem(CodeEntitySearchResultDto entity) {
        return KnowledgeResultItemDto.codeEntity(
                entity.qualifiedName(),
                entity.entityType() + " | " + nullSafe(entity.signature()),
                entity.filePath(),
                nullSafe(entity.declaringType()),
                null,
                List.of()
        );
    }

    private KnowledgeResultItemDto toSemanticItem(String projectKey, SemanticSearchResultDto semantic) {
        return KnowledgeResultItemDto.semantic(
                semantic.entityQualifiedName(),
                semantic.entityType() + " | " + semantic.docType(),
                semantic.filePath(),
                semantic.preview(),
                semantic.score(),
                topUpstreamHighlights(projectKey, semantic.entityQualifiedName()),
                Map.of(
                        "entityType", semantic.entityType(),
                        "docType", semantic.docType()
                )
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
            EndpointSearchResultDto representative = members.getFirst();

            List<String> highlights = members.stream()
                    .map(EndpointSearchResultDto::methodQualifiedName)
                    .distinct()
                    .limit(10)
                    .toList();

            results.add(KnowledgeResultItemDto.endpoint(
                    entry.getKey(),
                    "ROUTE_GROUP | " + members.size() + " mapped methods",
                    representative.filePath(),
                    "Exact route match with multiple mapped handlers",
                    members.stream().mapToInt(EndpointSearchResultDto::score).max().orElse(0),
                    highlights
            ));
        }

        return results.stream()
                .limit(limit)
                .toList();
    }

    private List<String> topUpstreamHighlights(String projectKey, String qualifiedName) {
        try {
            var trace = reverseTraversalService.trace(projectKey, qualifiedName, TraceDirection.UPSTREAM, 2, 10);
            return trace.upstream().stream()
                    .map(node -> node.qualifiedName())
                    .limit(3)
                    .toList();
        } catch (Exception ex) {
            log.debug(
                    "Unable to compute upstream highlights for project={} qualifiedName={}",
                    projectKey,
                    qualifiedName,
                    ex
            );
            return List.of();
        }
    }

    private record QueryContext(
            String projectKey,
            String query,
            int limit,
            boolean debug,
            boolean collapse,
            String entityType
    ) {
    }

    private List<String> extractSources(List<KnowledgeResultItemDto> results) {
        return results.stream()
                .map(item -> item.sourceType().name())
                .distinct()
                .toList();
    }

    private List<KnowledgeResultItemDto> rankMergedResults(List<KnowledgeResultItemDto> items, String query) {
        String normalizedQuery = query == null ? "" : query.toLowerCase(Locale.ROOT);

        return items.stream()
                .sorted(Comparator
                        .comparingInt((KnowledgeResultItemDto item) -> boostedScore(item, normalizedQuery))
                        .reversed()
                        .thenComparing(KnowledgeResultItemDto::title, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }

    private int boostedScore(KnowledgeResultItemDto item, String normalizedQuery) {
        int base = score(item);

        if (item.sourceType() == com.vena.codesage.dto.KnowledgeSourceType.CONFLUENCE && item.metadata() != null) {
            Object classNames = item.metadata().get("classNames");
            Object endpoints = item.metadata().get("endpoints");

            if (classNames instanceof List<?> list && !list.isEmpty()) {
                base += 8;
            }
            if (endpoints instanceof List<?> list && !list.isEmpty()) {
                base += 10;
            }
        }

        String title = item.title() == null ? "" : item.title().toLowerCase(Locale.ROOT);
        String snippet = item.snippet() == null ? "" : item.snippet().toLowerCase(Locale.ROOT);

        if (!normalizedQuery.isBlank() && title.contains(normalizedQuery)) {
            base += 15;
        } else if (!normalizedQuery.isBlank() && snippet.contains(normalizedQuery)) {
            base += 5;
        }

        return base;
    }

}