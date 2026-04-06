package com.vena.codesage.dto;

public record EndpointSearchResultDto(
        String httpMethod,
        String path,
        String methodQualifiedName,
        String filePath,
        boolean unresolvedPath,
        int score
) {
}