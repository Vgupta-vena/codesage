package com.vena.codesage.dto;

import java.util.List;

public record TraceResponseDto(
        String projectKey,
        String seedQualifiedName,
        TraceDirection direction,
        int depth,
        List<TraceNodeDto> upstream,
        List<TraceNodeDto> downstream,
        List<KnowledgeResultItemDto> reachableEndpoints,
        String summary
) {
}