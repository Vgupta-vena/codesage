package com.vena.sageorchestrator.codesage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "brain.codesage")
public record CodeSageProperties(
        String baseUrl
) {}
