package com.vena.codesage.dto;

public record CodeEntitySearchResultDto(
        String entityType,
        String qualifiedName,
        String declaringType,
        String signature,
        String filePath
) {}