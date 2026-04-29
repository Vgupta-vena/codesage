package com.vena.codesage.dto;

public record BlastRadiusCountsDto(
        int directCallers,
        int directCallees,
        int reachableEndpoints,
        int directEndpointMappings
) {}
