package com.vena.codesage.dto;

public record SemanticDocumentDto(
        String entityQualifiedName,
        String entityType,
        String docType,
        String filePath,
        String content
) {}