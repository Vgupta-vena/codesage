package com.vena.codesage.dto;

public record CompleteScanRequest(
        Long scanRunId,
        boolean activate,
        String metadataJson
) {}