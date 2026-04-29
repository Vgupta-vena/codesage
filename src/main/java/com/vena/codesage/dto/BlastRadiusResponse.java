package com.vena.codesage.dto;

import java.util.List;

public record BlastRadiusResponse(
        String projectKey,
        String symbol,
        String filePath,
        boolean isEndpointHandler,
        List<EndpointRouteDto> directRoutes,
        List<String> directCallers,
        List<String> directCallees,
        List<String> reachableEndpoints,
        BlastRadiusCountsDto counts,
        List<EvidenceItemDto> evidence
) {}
