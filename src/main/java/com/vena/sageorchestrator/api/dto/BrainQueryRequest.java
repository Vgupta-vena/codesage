package com.vena.sageorchestrator.api.dto;

cimport jakarta.validation.constraints.NotBlank;

public record BrainQueryRequest(
        @NotBlank String projectKey,
        @NotBlank String question
) {}
