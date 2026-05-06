package com.vena.sageorchestrator.common.config;

import com.vena.sageorchestrator.codesage.CodeSageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        CodeSageProperties.class
})
public class AppConfig {
}
