package com.vena.codesage.dto;

import java.util.List;

public record KnowledgeResponseDto(
        String projectKey,
        String query,
        KnowledgeMode modeUsed,
        String summary,
        List<KnowledgeResultItemDto> results
) {
}