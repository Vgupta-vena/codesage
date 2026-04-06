package com.vena.codesage.ingestion;

public record IngestionRequest(
        Long scanRunId,
        String projectKey,
        String baseDir
) {}