package com.vena.codesage.integration.jira;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.jira")
public record JiraProperties(
        boolean enabled,
        String baseUrl,
        String username,
        String apiToken,
        String projectKey,
        int connectTimeoutMillis,
        int readTimeoutMillis
) {
    public JiraProperties {
        connectTimeoutMillis = connectTimeoutMillis <= 0 ? 5000 : connectTimeoutMillis;
        readTimeoutMillis = readTimeoutMillis <= 0 ? 10000 : readTimeoutMillis;
    }

    public boolean isConfigured() {
        return hasText(baseUrl) && hasText(username) && hasText(apiToken);
    }

    public void validateIfEnabled() {
        if (!enabled) {
            return;
        }

        if (!hasText(baseUrl)) {
            throw new IllegalStateException("Jira is enabled but app.jira.base-url is missing");
        }
        if (!hasText(username)) {
            throw new IllegalStateException("Jira is enabled but app.jira.username is missing");
        }
        if (!hasText(apiToken)) {
            throw new IllegalStateException("Jira is enabled but app.jira.api-token is missing");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}