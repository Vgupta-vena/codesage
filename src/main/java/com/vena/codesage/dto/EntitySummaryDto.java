package com.vena.codesage.dto;

import java.util.List;

public record EntitySummaryDto(
        String qualifiedName,
        String filePath,
        String summary,
        List<String> methods,
        List<String> calls,
        List<String> calledBy,
        List<String> touchpoints,
        List<String> endpointsReaching
) {}