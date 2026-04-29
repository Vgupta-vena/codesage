package com.vena.codesage.dto;

import java.util.List;

public record ReachableEndpointsResponse(
        String projectKey,
        String symbol,
        boolean isEndpointHandler,
        List<EndpointRouteDto> directRoutes,
        List<String> reachableEndpoints,
        String message,
        String filePath
) {}