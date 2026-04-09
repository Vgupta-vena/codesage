package com.vena.codesage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KnowledgeResponseDto(
        String projectKey,
        String query,
        KnowledgeMode mode,
        String summary,
        List<KnowledgeResultItemDto> results,

        Integer resultCount,
        List<String> sourcesUsed,
        Long elapsedMs
) {

    public KnowledgeResponseDto {
        results = results == null ? List.of() : List.copyOf(results);
        resultCount = resultCount == null ? results.size() : resultCount;
        sourcesUsed = sourcesUsed == null || sourcesUsed.isEmpty() ? null : List.copyOf(sourcesUsed);
    }

    public static KnowledgeResponseDto simple(
            String projectKey,
            String query,
            KnowledgeMode mode,
            String summary,
            List<KnowledgeResultItemDto> results
    ) {
        return new KnowledgeResponseDto(
                projectKey,
                query,
                mode,
                summary,
                results,
                results == null ? 0 : results.size(),
                null,
                null
        );
    }
}