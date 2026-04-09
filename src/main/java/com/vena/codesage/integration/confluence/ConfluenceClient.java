package com.vena.codesage.integration.confluence;

import com.vena.codesage.integration.atlassian.AtlassianRestClientFactory;
import com.vena.codesage.integration.atlassian.AtlassianProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

@Component
public class ConfluenceClient {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceClient.class);

    private final AtlassianRestClientFactory clientFactory;
    private final AtlassianProperties properties;

    public ConfluenceClient(AtlassianRestClientFactory clientFactory) {
        this.clientFactory = clientFactory;
        this.properties = clientFactory.properties();
    }

    public List<ConfluencePage> searchPages(String query, int limit) {
        if (!clientFactory.isEnabled() || !properties.confluence().enabled()) {
            return List.of();
        }

        RestClient restClient = clientFactory.restClient();
        int effectiveLimit = Math.min(Math.max(limit, 1), properties.confluence().maxResults());
        String cql = buildCql(query, properties.confluence().spaceKey());

        log.info("Confluence search start query={} limit={} spaceKey={}",
                query, effectiveLimit, properties.confluence().spaceKey());

        try {
            Map<String, Object> response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/wiki/rest/api/search")
                            .queryParam("cql", cql)
                            .queryParam("limit", effectiveLimit)
                            .queryParam("expand", "content.body.view,content.space")
                            .build())
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> results = response == null
                    ? List.of()
                    : (List<Map<String, Object>>) response.getOrDefault("results", List.of());

            List<ConfluencePage> pages = results.stream()
                    .map(this::toPage)
                    .filter(page -> page != null)
                    .toList();

            log.info("Confluence search complete query={} results={}", query, pages.size());
            return pages;
        } catch (RestClientException ex) {
            log.warn("Confluence search failed query={} error={}", query, ex.getMessage());
            return List.of();
        } catch (Exception ex) {
            log.error("Unexpected Confluence search failure query={}", query, ex);
            return List.of();
        }
    }

    private String buildCql(String query, String spaceKey) {
        String escaped = query == null ? "" : query.replace("\"", "\\\"");
        if (spaceKey != null && !spaceKey.isBlank()) {
            return "siteSearch ~ \"" + escaped + "\" AND space = \"" + spaceKey + "\"";
        }
        return "siteSearch ~ \"" + escaped + "\"";
    }

    @SuppressWarnings("unchecked")
    private ConfluencePage toPage(Map<String, Object> raw) {
        try {
            Map<String, Object> content = raw.get("content") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : null;

            if (content == null) {
                return null;
            }

            String id = stringValue(content.get("id"));
            String title = stringValue(content.get("title"));

            Map<String, Object> space = content.get("space") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : null;

            String spaceKey = space == null ? null : stringValue(space.get("key"));

            Map<String, Object> body = content.get("body") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : null;

            Map<String, Object> view = body != null && body.get("view") instanceof Map<?, ?> map
                    ? (Map<String, Object>) map
                    : null;

            String html = view == null ? "" : stringValue(view.get("value"));
            String text = htmlToText(html);
            String excerpt = abbreviate(text, 280);
            String webUrl = properties.baseUrl() + "/wiki/pages/viewpage.action?pageId=" + id;

            return new ConfluencePage(id, title, spaceKey, webUrl, excerpt, text);
        } catch (Exception ex) {
            log.debug("Failed to map Confluence result raw={}", raw, ex);
            return null;
        }
    }

    private String htmlToText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return html
                .replaceAll("(?is)<script.*?>.*?</script>", " ")
                .replaceAll("(?is)<style.*?>.*?</style>", " ")
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)</p>", "\n")
                .replaceAll("(?is)<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String abbreviate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}