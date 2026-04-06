package com.vena.codesage.dto;

public record IngestionResultDto(
        String jobName,
        int recordsProcessed
) {}