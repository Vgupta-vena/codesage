package com.vena.codesage.graph.service.ranking;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "codesage.ranking")
public record RankingWeights(
        int exactQualifiedNameMatch,
        int exactSimpleNameMatch,
        int exactPhraseMatch,
        int lexicalExactMatch,
        int primaryIdentifierMatch,
        int expandedIdentifierMatch,
        int primarySimpleNameMatch,
        int expandedSimpleNameMatch,
        int summaryMatch,
        int touchpointMatch,
        int endpointMatch,
        int calledByMatch,
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