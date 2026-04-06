package com.vena.codesage.dto;

import java.util.List;

public record KnowledgeResultItemDto(
        String resultType,
        String title,
        String subtitle,
        String location,
        String preview,
        Integer score,
        List<String> highlights
) {
}