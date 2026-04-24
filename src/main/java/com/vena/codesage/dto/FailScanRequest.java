package com.vena.codesage.dto;

public record FailScanRequest(
        Long scanRunId,
        String metadataJson
) {}
