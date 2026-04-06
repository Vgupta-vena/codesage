package com.vena.codesage.graph.service.ranking;

import com.vena.codesage.graph.model.SemanticDocument;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SemanticRanker {

    private static final Set<String> ACTION_VOCABULARY = Set.of(
            "create", "add", "insert", "save", "persist",
            "update", "patch", "modify", "change",
            "delete", "remove", "purge",
            "get", "fetch", "find", "load",
            "list", "search", "sync", "validate"
    );

    private static final Set<String> LOW_SIGNAL_IDENTIFIER_TOKENS = Set.of(
            "com", "org", "net", "io",
            "service", "impl", "util", "utils", "helper", "helpers",
            "manager", "client", "controller", "resource", "api",
            "core", "common", "base", "internal",
            "src", "main", "java"
    );

    private final RankingWeights weights;
    private final TokenNormalizer tokenNormalizer;

    public SemanticRanker(RankingWeights weights, TokenNormalizer tokenNormalizer) {
        this.weights = weights;
        this.tokenNormalizer = tokenNormalizer;
    }

    public int score(SemanticDocument document, String query) {
        return rank(document, query, false).score();
    }

    public RankedDocument rank(SemanticDocument document, String query, boolean debugEnabled) {
        QueryProfile queryProfile = buildQueryProfile(query);
        CandidateProfile candidateProfile = buildCandidateProfile(document);

        if (queryProfile.primaryTokens().isEmpty()) {
            return new RankedDocument(0, null);
        }

        RankingFeatures features = computeFeatures(queryProfile, candidateProfile);
        int score = Math.max(toScore(features), 0);

        RankingExplanation debug = debugEnabled
                ? buildExplanation(queryProfile, candidateProfile, features, score)
                : null;

        return new RankedDocument(score, debug);
    }

    private QueryProfile buildQueryProfile(String query) {
        Set<String> primary = tokenNormalizer.tokenizeQuery(query);
        Set<String> expanded = expandTokens(primary);

        Set<String> actionTokens = inferActionTokens(primary, expanded);
        Set<String> objectTokens = new LinkedHashSet<>(primary);
        objectTokens.removeAll(actionTokens);

        return new QueryProfile(
                query == null ? "" : query.trim(),
                List.copyOf(primary),
                expanded.stream().filter(token -> !primary.contains(token)).toList(),
                actionTokens,
                objectTokens
        );
    }

    private CandidateProfile buildCandidateProfile(SemanticDocument document) {
        String qualifiedName = defaultString(document.getEntityQualifiedName());
        String simpleName = tokenNormalizer.extractSimpleName(qualifiedName);
        String filePath = defaultString(document.getFilePath());
        String content = defaultString(document.getContent());

        String summary = extractSection(content, "Summary:");
        String touchpoints = extractSection(content, "Direct Touchpoints:");
        String endpoints = extractSection(content, "Endpoints Reaching:");
        String calledBy = extractSection(content, "Called By:");

        Set<String> identifierTokens = new LinkedHashSet<>();
        identifierTokens.addAll(tokenNormalizer.tokenizeIdentifier(qualifiedName));
        identifierTokens.addAll(tokenNormalizer.tokenizeIdentifier(simpleName));
        identifierTokens.addAll(tokenNormalizer.tokenizeIdentifier(filePath));

        Set<String> simpleNameTokens = tokenNormalizer.tokenizeIdentifier(simpleName);

        return new CandidateProfile(
                qualifiedName,
                simpleName,
                filePath,
                content,
                summary,
                touchpoints,
                endpoints,
                calledBy,
                identifierTokens,
                simpleNameTokens,
                tokenNormalizer.tokenizeSection(summary),
                tokenNormalizer.tokenizeSection(touchpoints),
                tokenNormalizer.tokenizeSection(endpoints),
                tokenNormalizer.tokenizeSection(calledBy),
                tokenNormalizer.hasRealEntries(touchpoints),
                tokenNormalizer.hasRealEntries(endpoints)
        );
    }

    private RankingFeatures computeFeatures(QueryProfile query, CandidateProfile candidate) {
        String loweredQuery = query.raw().toLowerCase(Locale.ROOT).trim();
        String loweredQualifiedName = candidate.qualifiedName().toLowerCase(Locale.ROOT);
        String loweredSimpleName = candidate.simpleName().toLowerCase(Locale.ROOT);
        String loweredContent = candidate.content().toLowerCase(Locale.ROOT);

        String normalizedSimpleNamePhrase = normalizePhrase(candidate.simpleName());
        String normalizedQueryPhrase = normalizePhrase(query.raw());

        int exactQualifiedNameMatch = loweredQualifiedName.equals(loweredQuery) ? 1 : 0;
        int exactSimpleNameMatch = loweredSimpleName.equals(loweredQuery) ? 1 : 0;

        int exactPhraseMatch = 0;
        if (!normalizedQueryPhrase.isBlank()) {
            if (normalizedSimpleNamePhrase.equals(normalizedQueryPhrase)) {
                exactPhraseMatch = 2;
            } else if (loweredContent.contains(loweredQuery)) {
                exactPhraseMatch = 1;
            }
        }

        int lexicalExactMatch = !loweredQuery.isBlank() && loweredSimpleName.contains(loweredQuery) ? 1 : 0;

        int primaryIdentifierMatches = overlapCount(query.primaryTokens(), candidate.identifierTokens());
        int expandedIdentifierMatches = overlapCount(query.expandedTokens(), candidate.identifierTokens());

        int primarySimpleNameMatches = overlapCount(query.primaryTokens(), candidate.simpleNameTokens());
        int expandedSimpleNameMatches = overlapCount(query.expandedTokens(), candidate.simpleNameTokens());

        int summaryMatches = overlapCount(query.primaryTokens(), candidate.summaryTokens());
        int touchpointMatches = overlapCount(query.primaryTokens(), candidate.touchpointTokens());
        int endpointMatches = overlapCount(query.primaryTokens(), candidate.endpointTokens());
        int calledByMatches = overlapCount(query.primaryTokens(), candidate.calledByTokens());

        int allPrimaryMatched = !query.primaryTokens().isEmpty()
                && candidate.identifierTokens().containsAll(query.primaryTokens()) ? 1 : 0;

        int allPrimarySimpleNameMatched = !query.primaryTokens().isEmpty()
                && candidate.simpleNameTokens().containsAll(query.primaryTokens()) ? 1 : 0;

        int proximityMatch = query.primaryTokens().size() > 1
                && tokenNormalizer.containsTokensNearEachOther(candidate.content(), Set.copyOf(query.primaryTokens()), 10)
                ? 1 : 0;

        int realTouchpointSignal = candidate.hasRealTouchpoints() ? 1 : 0;
        int realEndpointSignal = candidate.hasRealEndpoints() ? 1 : 0;

        Set<String> candidateActionTokens = candidate.simpleNameTokens().stream()
                .map(this::normalizeAction)
                .filter(ACTION_VOCABULARY::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> normalizedQueryActions = query.actionTokens().stream()
                .map(this::normalizeAction)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int actionAlignment = overlapCount(normalizedQueryActions, candidateActionTokens);

        Set<String> filteredSimpleNameTokens = filterLowSignalTokens(candidate.simpleNameTokens());
        int objectAlignment = overlapCount(query.objectTokens(), filteredSimpleNameTokens);

        int objectDriftPenalty = computeObjectDriftPenalty(query, filteredSimpleNameTokens);
        int compoundActionPenalty = computeCompoundActionPenalty(query, candidate.simpleNameTokens());
        int identifierPrecisionPenalty = computeIdentifierPrecisionPenalty(query, candidate);

        return new RankingFeatures(
                exactQualifiedNameMatch,
                exactSimpleNameMatch,
                exactPhraseMatch,
                lexicalExactMatch,
                primaryIdentifierMatches,
                expandedIdentifierMatches,
                primarySimpleNameMatches,
                expandedSimpleNameMatches,
                summaryMatches,
                touchpointMatches,
                endpointMatches,
                calledByMatches,
                allPrimaryMatched,
                allPrimarySimpleNameMatched,
                proximityMatch,
                realTouchpointSignal,
                realEndpointSignal,
                actionAlignment,
                objectAlignment,
                objectDriftPenalty,
                compoundActionPenalty,
                identifierPrecisionPenalty
        );
    }

    private int toScore(RankingFeatures features) {
        return features.exactQualifiedNameMatch() * weights.exactQualifiedNameMatch()
                + features.exactSimpleNameMatch() * weights.exactSimpleNameMatch()
                + features.exactPhraseMatch() * weights.exactPhraseMatch()
                + features.lexicalExactMatch() * weights.lexicalExactMatch()
                + features.primaryIdentifierMatches() * weights.primaryIdentifierMatch()
                + features.expandedIdentifierMatches() * weights.expandedIdentifierMatch()
                + features.primarySimpleNameMatches() * weights.primarySimpleNameMatch()
                + features.expandedSimpleNameMatches() * weights.expandedSimpleNameMatch()
                + features.summaryMatches() * weights.summaryMatch()
                + features.touchpointMatches() * weights.touchpointMatch()
                + features.endpointMatches() * weights.endpointMatch()
                + features.calledByMatches() * weights.calledByMatch()
                + features.allPrimaryMatched() * weights.allPrimaryMatched()
                + features.allPrimarySimpleNameMatched() * weights.allPrimarySimpleNameMatched()
                + features.proximityMatch() * weights.proximityMatch()
                + features.realTouchpointSignal() * weights.realTouchpointSignal()
                + features.realEndpointSignal() * weights.realEndpointSignal()
                + features.actionAlignment() * weights.actionAlignment()
                + features.objectAlignment() * weights.objectAlignment()
                - features.objectDriftPenalty() * weights.objectDriftPenalty()
                - features.compoundActionPenalty() * weights.compoundActionPenalty()
                - features.identifierPrecisionPenalty() * weights.identifierPrecisionPenalty();
    }

    private RankingExplanation buildExplanation(QueryProfile query,
                                                CandidateProfile candidate,
                                                RankingFeatures features,
                                                int finalScore) {
        return new RankingExplanation(
                Map.of(
                        "raw", query.raw(),
                        "primaryTokens", query.primaryTokens(),
                        "expandedTokens", query.expandedTokens(),
                        "actionTokens", query.actionTokens(),
                        "objectTokens", query.objectTokens()
                ),
                Map.of(
                        "qualifiedName", candidate.qualifiedName(),
                        "simpleName", candidate.simpleName(),
                        "simpleNameTokens", candidate.simpleNameTokens()
                ),
                Map.of(
                        "exactQualifiedNameMatch", features.exactQualifiedNameMatch(),
                        "exactSimpleNameMatch", features.exactSimpleNameMatch(),
                        "exactPhraseMatch", features.exactPhraseMatch(),
                        "lexicalExactMatch", features.lexicalExactMatch(),
                        "primaryIdentifierMatches", features.primaryIdentifierMatches(),
                        "expandedIdentifierMatches", features.expandedIdentifierMatches(),
                        "primarySimpleNameMatches", features.primarySimpleNameMatches(),
                        "expandedSimpleNameMatches", features.expandedSimpleNameMatches(),
                        "allPrimaryMatched", features.allPrimaryMatched(),
                        "allPrimarySimpleNameMatched", features.allPrimarySimpleNameMatched()
                ),
                Map.of(
                        "summaryMatches", features.summaryMatches(),
                        "touchpointMatches", features.touchpointMatches(),
                        "endpointMatches", features.endpointMatches(),
                        "calledByMatches", features.calledByMatches(),
                        "proximityMatch", features.proximityMatch(),
                        "realTouchpointSignal", features.realTouchpointSignal(),
                        "realEndpointSignal", features.realEndpointSignal(),
                        "actionAlignment", features.actionAlignment(),
                        "objectAlignment", features.objectAlignment()
                ),
                Map.of(
                        "objectDriftPenalty", features.objectDriftPenalty(),
                        "compoundActionPenalty", features.compoundActionPenalty(),
                        "identifierPrecisionPenalty", features.identifierPrecisionPenalty()
                ),
                finalScore
        );
    }

    private int computeObjectDriftPenalty(QueryProfile query, Set<String> filteredSimpleNameTokens) {
        if (query.objectTokens().isEmpty()) {
            return 0;
        }

        int penalty = 0;
        for (String token : filteredSimpleNameTokens) {
            boolean isAction = ACTION_VOCABULARY.contains(normalizeAction(token));
            boolean matchesObject = query.objectTokens().contains(token);
            boolean matchesExpanded = query.expandedTokens().contains(token);

            if (!isAction && !matchesObject && !matchesExpanded) {
                penalty++;
            }
        }

        return penalty;
    }

    private int computeCompoundActionPenalty(QueryProfile query, Set<String> simpleNameTokens) {
        if (query.actionTokens().isEmpty()) {
            return 0;
        }

        long distinctActions = simpleNameTokens.stream()
                .map(this::normalizeAction)
                .filter(ACTION_VOCABULARY::contains)
                .distinct()
                .count();

        return distinctActions > 1 ? (int) distinctActions - 1 : 0;
    }

    private int computeIdentifierPrecisionPenalty(QueryProfile query, CandidateProfile candidate) {
        Set<String> filteredIdentifierTokens = filterLowSignalTokens(candidate.identifierTokens());

        int penalty = 0;
        for (String token : filteredIdentifierTokens) {
            boolean matchedPrimary = query.primaryTokens().contains(token);
            boolean matchedExpanded = query.expandedTokens().contains(token);
            boolean matchedAction = query.actionTokens().stream()
                    .map(this::normalizeAction)
                    .anyMatch(action -> action.equals(normalizeAction(token)));

            if (!matchedPrimary && !matchedExpanded && !matchedAction) {
                penalty++;
            }
        }

        return penalty;
    }

    private Set<String> filterLowSignalTokens(Set<String> tokens) {
        return tokens.stream()
                .filter(token -> token != null && !token.isBlank())
                .filter(token -> token.length() > 2)
                .filter(token -> !LOW_SIGNAL_IDENTIFIER_TOKENS.contains(token))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> expandTokens(Set<String> primaryTokens) {
        Set<String> expanded = new LinkedHashSet<>(primaryTokens);

        for (String token : primaryTokens) {
            switch (normalizeAction(token)) {
                case "update" -> {
                    expanded.add("patch");
                    expanded.add("modify");
                    expanded.add("change");
                }
                case "create" -> {
                    expanded.add("add");
                    expanded.add("insert");
                    expanded.add("save");
                    expanded.add("persist");
                }
                case "delete" -> {
                    expanded.add("remove");
                    expanded.add("purge");
                }
                case "get" -> {
                    expanded.add("find");
                    expanded.add("fetch");
                    expanded.add("load");
                }
                default -> {
                }
            }
        }

        return expanded;
    }

    private Set<String> inferActionTokens(Set<String> primaryTokens, Set<String> expandedTokens) {
        Set<String> actionTokens = new LinkedHashSet<>();

        for (String token : primaryTokens) {
            String normalized = normalizeAction(token);
            if (ACTION_VOCABULARY.contains(normalized)) {
                actionTokens.add(normalized);
            }
        }

        if (actionTokens.isEmpty()) {
            for (String token : expandedTokens) {
                String normalized = normalizeAction(token);
                if (ACTION_VOCABULARY.contains(normalized)) {
                    actionTokens.add(normalized);
                }
            }
        }

        return actionTokens;
    }

    private String normalizeAction(String token) {
        if (token == null || token.isBlank()) {
            return "";
        }

        return switch (token.toLowerCase(Locale.ROOT)) {
            case "updated", "updating", "patch", "patched", "patching", "modify", "modified", "modifying", "change", "changed", "changing" -> "update";
            case "created", "creating", "add", "added", "adding", "insert", "inserted", "inserting", "save", "saved", "saving", "persist", "persisted", "persisting" -> "create";
            case "deleted", "deleting", "remove", "removed", "removing", "purge", "purged", "purging" -> "delete";
            case "fetch", "fetched", "fetching", "find", "found", "load", "loaded", "loading" -> "get";
            default -> token.toLowerCase(Locale.ROOT);
        };
    }

    private String normalizePhrase(String value) {
        return tokenNormalizer.tokenizeIdentifier(value).stream()
                .collect(Collectors.joining(" "));
    }

    private int overlapCount(Iterable<String> queryTokens, Set<String> candidateTokens) {
        int count = 0;
        for (String token : queryTokens) {
            if (candidateTokens.contains(token)) {
                count++;
            }
        }
        return count;
    }

    private String extractSection(String content, String header) {
        if (content == null || content.isBlank()) {
            return "";
        }

        int start = content.indexOf(header);
        if (start < 0) {
            return "";
        }

        int from = start + header.length();
        int nextHeader = content.indexOf("\n\n", from);
        if (nextHeader < 0) {
            return content.substring(from).trim();
        }

        return content.substring(from, nextHeader).trim();
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}