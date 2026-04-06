package com.vena.codesage.graph.service.ranking;

public record RankedDocument(
        int score,
        RankingExplanation debug
) {
}
