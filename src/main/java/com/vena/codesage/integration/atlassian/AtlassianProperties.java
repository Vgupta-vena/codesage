package com.vena.codesage.integration.atlassian;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.atlassian")
public record AtlassianProperties(
        boolean enabled,
        String baseUrl,
        String username,
        String apiToken,
        int connectTimeoutMillis,
        int readTimeoutMillis,
        Jira jira,
        Confluence confluence
) {
    public AtlassianProperties {
        connectTimeoutMillis = connectTimeoutMillis <= 0 ? 5000 : connectTimeoutMillis;
        readTimeoutMillis = readTimeoutMillis <= 0 ? 10000 : readTimeoutMillis;
        jira = jira == null ? new Jira(false, "") : jira;
        confluence = confluence == null ? new Confluence(false, "", 10) : confluence;
    }

    public boolean isConfigured() {
        return hasText(baseUrl) && hasText(username) && hasText(apiToken);
    }

    public void validateIfEnabled() {
        if (!enabled) {
            return;
        }

        if (!hasText(baseUrl)) {
            throw new IllegalStateException("Atlassian integration is enabled but app.atlassian.base-url is missing");
        }
        if (!hasText(username)) {
            throw new IllegalStateException("Atlassian integration is enabled but app.atlassian.username is missing");
        }
        if (!hasText(apiToken)) {
            throw new IllegalStateException("Atlassian integration is enabled but app.atlassian.api-token is missing");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record Jira(
            boolean enabled,
            String projectKey
    ) {
    }

    public record Confluence(
            boolean enabled,
            String spaceKey,
            int maxResults
    ) {
        public Confluence {
            maxResults = maxResults <= 0 ? 10 : maxResults;
        }
    }
}