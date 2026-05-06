package com.vena.sageorchestrator.codesage.dto;

import java.util.List;

public record EndpointMappingResponse(
        String projectKey,
        String symbol,
        String filePath,
        List<EndpointRouteDto> routes
) {}
