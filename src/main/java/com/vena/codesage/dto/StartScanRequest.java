package com.vena.codesage.dto;

public record StartScanRequest(
        String projectKey,
        String sourceType,
        String sourceRevision,
        String metadataJson
) {}