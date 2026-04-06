package com.vena.codesage.dto;

import com.vena.codesage.graph.service.ranking.RankingExplanation;

public record SemanticSearchResultDto(
        String entityQualifiedName,
        String entityType,
        String docType,
        String filePath,
        String preview,
        int score,
        RankingExplanation debug
) {
}