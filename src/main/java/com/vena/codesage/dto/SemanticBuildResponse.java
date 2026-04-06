package com.vena.codesage.dto;

public record SemanticBuildResponse(
        Long scanRunId,
        String projectKey,
        int documentsBuilt
) {}
