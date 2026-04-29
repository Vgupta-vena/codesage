package com.vena.codesage.dto;

import java.util.List;

public record ExplainResponse(
        String projectKey,
        String symbol,
        String filePath,
        List<EndpointRouteDto> directRoutes,
        List<String> directCallers,
        List<String> directCallees,
        List<EvidenceItemDto> evidence,
        String summary
) {}
