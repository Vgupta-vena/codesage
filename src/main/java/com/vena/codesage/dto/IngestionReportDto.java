package com.vena.codesage.dto;

import java.util.List;

public record IngestionReportDto(
        Long scanRunId,
        String projectKey,
        String status,
        boolean active,
        List<IngestionResultDto> results
) {}