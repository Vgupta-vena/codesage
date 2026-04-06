package com.vena.codesage.dto;

import jakarta.validation.constraints.NotBlank;

public record AskRequest(
        @NotBlank String projectKey,
        @NotBlank String question
) {}