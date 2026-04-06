package com.vena.codesage.dto;

public record TraceNodeDto(
        String qualifiedName,
        String entityType,
        String filePath,
        int depth,
        String relation
) {
}
