package com.vena.codesage.dto;

import java.time.LocalDateTime;

public record StartScanResponse(
        Long scanRunId,
        String projectKey,
        String sourceType,
        String sourceRevision,
        String status,
        boolean active,
        LocalDateTime startedAt
) {}