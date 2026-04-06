package com.vena.codesage.dto;

import java.util.List;

public record BlastRadiusDto(
        String targetMethod,
        int depth,
        List<String> callers,
        List<String> callees,
        List<String> impactedEndpoints,
        List<String> touchpoints,
        List<String> reachableTouchpoints,
        List<String> externalLikeCallees,
        List<String> persistenceLikeCallees
) {}