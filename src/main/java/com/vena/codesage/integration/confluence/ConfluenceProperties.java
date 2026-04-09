package com.vena.codesage.integration.confluence;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.confluence")
public record ConfluenceProperties(
        boolean enabled,
        String baseUrl,
        String username,
        String apiToken,
        String spaceKey,
        int connectTimeoutMillis,
        int readTimeoutMillis,
        int maxResults
) {
    public ConfluenceProperties {
        connectTimeoutMillis = connectTimeoutMillis <= 0 ? 5000 : connectTimeoutMillis;
        readTimeoutMillis = readTimeoutMillis <= 0 ? 10000 : readTimeoutMillis;
        maxResults = maxResults <= 0 ? 10 : maxResults;
    }

    public boolean isConfigured() {
        return hasText(baseUrl) && hasText(username) && hasText(apiToken);
    }

    public void validateIfEnabled() {
        if (!enabled) {
            return;
        }

        if (!hasText(baseUrl)) {
            throw new IllegalStateException("Confluence is enabled but app.confluence.base-url is missing");
        }
        if (!hasText(username)) {
            throw new IllegalStateException("Confluence is enabled but app.confluence.username is missing");
        }
        if (!hasText(apiToken)) {
            throw new IllegalStateException("Confluence is enabled but app.confluence.api-token is missing");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}