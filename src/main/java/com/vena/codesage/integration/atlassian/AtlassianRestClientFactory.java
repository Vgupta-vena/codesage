package com.vena.codesage.integration.atlassian;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

@Component
public class AtlassianRestClientFactory {

    private static final Logger log = LoggerFactory.getLogger(AtlassianRestClientFactory.class);

    private final AtlassianProperties properties;
    private RestClient restClient;

    public AtlassianRestClientFactory(AtlassianProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void init() {
        if (!properties.enabled()) {
            log.info("Atlassian integration is disabled");
            return;
        }

        properties.validateIfEnabled();

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.connectTimeoutMillis()))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(properties.readTimeoutMillis()));

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, basicAuth(properties.username(), properties.apiToken()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();

        log.info("Atlassian RestClient initialized baseUrl={}", properties.baseUrl());
    }

    public boolean isEnabled() {
        return properties.enabled() && properties.isConfigured();
    }

    public RestClient restClient() {
        return restClient;
    }

    public AtlassianProperties properties() {
        return properties;
    }

    private String basicAuth(String username, String token) {
        String raw = username + ":" + token;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}