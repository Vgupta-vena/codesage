package com.vena.codesage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KnowledgeResultItemDto(
        String resultType,
        KnowledgeSourceType sourceType,
        String sourceId,
        String title,
        String subtitle,
        String location,
        String snippet,
        Integer score,
        List<String> highlights,
        Map<String, Object> metadata
) {

    public KnowledgeResultItemDto {
        highlights = highlights == null ? List.of() : List.copyOf(highlights);
        metadata = metadata == null || metadata.isEmpty() ? null : Map.copyOf(metadata);
    }

    public static KnowledgeResultItemDto codeEntity(
            String title,
            String subtitle,
            String location,
            String snippet,
            Integer score,
            List<String> highlights
    ) {
        return new KnowledgeResultItemDto(
                "ENTITY",
                KnowledgeSourceType.CODE,
                null,
                title,
                subtitle,
                location,
                snippet,
                score,
                highlights,
                null
        );
    }

    public static KnowledgeResultItemDto endpoint(
            String title,
            String subtitle,
            String location,
            String snippet,
            Integer score,
            List<String> highlights
    ) {
        return new KnowledgeResultItemDto(
                "ENDPOINT",
                KnowledgeSourceType.CODE,
                null,
                title,
                subtitle,
                location,
                snippet,
                score,
                highlights,
                null
        );
    }

    public static KnowledgeResultItemDto semantic(
            String title,
            String subtitle,
            String location,
            String snippet,
            Integer score,
            List<String> highlights,
            Map<String, Object> metadata
    ) {
        return new KnowledgeResultItemDto(
                "SEMANTIC",
                KnowledgeSourceType.CODE,
                null,
                title,
                subtitle,
                location,
                snippet,
                score,
                highlights,
                metadata
        );
    }

    public static KnowledgeResultItemDto external(
            String resultType,
            KnowledgeSourceType sourceType,
            String sourceId,
            String title,
            String subtitle,
            String location,
            String snippet,
            Integer score,
            List<String> highlights,
            Map<String, Object> metadata
    ) {
        return new KnowledgeResultItemDto(
                resultType,
                sourceType,
                sourceId,
                title,
                subtitle,
                location,
                snippet,
                score,
                highlights,
                metadata
        );
    }
}