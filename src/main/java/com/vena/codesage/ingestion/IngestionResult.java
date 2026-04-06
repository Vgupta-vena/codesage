package com.vena.codesage.ingestion;

public record IngestionResult(
        String jobName,
        int recordsProcessed
) {}