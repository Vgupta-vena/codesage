package com.vena.codesage.graph.service.ranking;

public record RankingFeatures(
        int exactQualifiedNameMatch,
        int exactSimpleNameMatch,
        int exactPhraseMatch,
        int lexicalExactMatch,
        int primaryIdentifierMatches,
        int expandedIdentifierMatches,
        int primarySimpleNameMatches,
        int expandedSimpleNameMatches,
        int summaryMatches,
        int touchpointMatches,
        int endpointMatches,
        int calledByMatches,
        int allPrimaryMatched,
        int allPrimarySimpleNameMatched,
        int proximityMatch,
        int realTouchpointSignal,
        int realEndpointSignal,
        int actionAlignment,
        int objectAlignment,
        int objectDriftPenalty,
        int compoundActionPenalty,
        int identifierPrecisionPenalty
) {
}