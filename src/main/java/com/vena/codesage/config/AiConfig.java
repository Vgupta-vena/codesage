package com.vena.codesage.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiConfig.CodeSageAiProperties.class)
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @ConfigurationProperties(prefix = "codesage.ai")
    public record CodeSageAiProperties(
            boolean enabled,
            int maxKnowledgeResults,
            boolean includeDebugEvidence
    ) {
    }
}
