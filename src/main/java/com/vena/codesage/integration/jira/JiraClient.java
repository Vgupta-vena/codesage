package com.vena.codesage.integration.jira;

import com.vena.codesage.integration.atlassian.AtlassianProperties;
import com.vena.codesage.integration.atlassian.AtlassianRestClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Component
public class JiraClient {

    private static final Logger log = LoggerFactory.getLogger(JiraClient.class);

    private final AtlassianRestClientFactory clientFactory;
    private final AtlassianProperties properties;

    public JiraClient(AtlassianRestClientFactory clientFactory) {
        this.clientFactory = clientFactory;
        this.properties = clientFactory.properties();
    }

    public JiraIssue getIssue(String issueKey) {
        if (!clientFactory.isEnabled() || !properties.jira().enabled()) {
            return null;
        }

        RestClient restClient = clientFactory.restClient();

        try {
            Map<String, Object> response = restClient.get()
                    .uri("/rest/api/3/issue/{issueKey}", issueKey)
                    .retrieve()
                    .body(Map.class);

            return toIssue(response);
        } catch (RestClientException ex) {
            log.warn("Jira issue fetch failed issueKey={} error={}", issueKey, ex.getMessage());
            return null;
        } catch (Exception ex) {
            log.error("Unexpected Jira issue fetch failure issueKey={}", issueKey, ex);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private JiraIssue toIssue(Map<String, Object> raw) {
        if (raw == null) {
            return null;
        }

        String key = stringValue(raw.get("key"));
        Map<String, Object> fields = raw.get("fields") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : null;

        if (fields == null) {
            return null;
        }

        String summary = stringValue(fields.get("summary"));
        String description = extractDescription(fields.get("description"));
        String issueType = extractName(fields.get("issuetype"));
        String status = extractName(fields.get("status"));
        String priority = extractName(fields.get("priority"));
        String assignee = extractDisplayName(fields.get("assignee"));
        String webUrl = properties.baseUrl() + "/browse/" + key;

        return new JiraIssue(key, summary, description, issueType, status, priority, assignee, webUrl);
    }

    @SuppressWarnings("unchecked")
    private String extractDescription(Object value) {
        if (!(value instanceof Map<?, ?> doc)) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        extractText((Map<String, Object>) doc, sb);
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private void extractText(Map<String, Object> node, StringBuilder sb) {
        Object text = node.get("text");
        if (text instanceof String s && !s.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(s.trim());
        }

        Object content = node.get("content");
        if (content instanceof java.util.List<?> list) {
            for (Object child : list) {
                if (child instanceof Map<?, ?> childMap) {
                    extractText((Map<String, Object>) childMap, sb);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private String extractName(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringValue(((Map<String, Object>) map).get("name"));
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private String extractDisplayName(Object value) {
        if (value instanceof Map<?, ?> map) {
            return stringValue(((Map<String, Object>) map).get("displayName"));
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}