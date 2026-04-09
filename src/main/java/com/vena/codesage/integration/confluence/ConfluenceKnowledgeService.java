package com.vena.codesage.integration.confluence;

import com.vena.codesage.dto.KnowledgeResultItemDto;
import com.vena.codesage.dto.KnowledgeSourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ConfluenceKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(ConfluenceKnowledgeService.class);

    private final ConfluenceClient confluenceClient;
    private final ConfluenceProperties properties;
    private final ConfluenceSignalExtractor signalExtractor;

    public ConfluenceKnowledgeService(ConfluenceClient confluenceClient,
                                      ConfluenceProperties properties,
                                      ConfluenceSignalExtractor signalExtractor) {
        this.confluenceClient = confluenceClient;
        this.properties = properties;
        this.signalExtractor = signalExtractor;
    }

    public List<KnowledgeResultItemDto> search(String query, int limit) {
        if (!properties.enabled()) {
            return List.of();
        }

        long startNanos = System.nanoTime();

        List<KnowledgeResultItemDto> results = confluenceClient.searchPages(query, limit).stream()
                .map(page -> toKnowledgeItem(page, query))
                .toList();

        long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;

        log.info("Confluence knowledge search query={} results={} elapsedMs={}",
                query, results.size(), elapsedMillis);

        return results;
    }

    private KnowledgeResultItemDto toKnowledgeItem(ConfluencePage page, String query) {
        ConfluenceSignalExtractor.ExtractedSignals signals = signalExtractor.extract(page.content());
        int score = score(page, query, signals);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("spaceKey", page.spaceKey());
        metadata.put("url", page.webUrl());

        if (!signals.endpoints().isEmpty()) {
            metadata.put("endpoints", signals.endpoints());
        }
        if (!signals.classNames().isEmpty()) {
            metadata.put("classNames", signals.classNames());
        }
        if (!signals.qualifiedNames().isEmpty()) {
            metadata.put("qualifiedNames", signals.qualifiedNames());
        }

        List<String> highlights = buildHighlights(signals);

        return KnowledgeResultItemDto.external(
                "DOCUMENT",
                KnowledgeSourceType.CONFLUENCE,
                page.id(),
                page.title(),
                page.spaceKey() == null ? "Confluence Page" : "Confluence | " + page.spaceKey(),
                page.webUrl(),
                page.excerpt(),
                score,
                highlights,
                metadata
        );
    }

    private int score(ConfluencePage page, String query, ConfluenceSignalExtractor.ExtractedSignals signals) {
        int score = 60;

        String q = query == null ? "" : query.toLowerCase();
        String title = page.title() == null ? "" : page.title().toLowerCase();
        String excerpt = page.excerpt() == null ? "" : page.excerpt().toLowerCase();

        if (!q.isBlank() && title.contains(q)) {
            score += 20;
        } else if (!q.isBlank() && excerpt.contains(q)) {
            score += 10;
        }

        score += Math.min(signals.classNames().size() * 3, 12);
        score += Math.min(signals.endpoints().size() * 4, 12);
        score += Math.min(signals.qualifiedNames().size() * 2, 8);

        return Math.min(score, 100);
    }

    private List<String> buildHighlights(ConfluenceSignalExtractor.ExtractedSignals signals) {
        List<String> merged = new java.util.ArrayList<>();

        merged.addAll(signals.classNames().stream().limit(4).toList());
        merged.addAll(signals.endpoints().stream().limit(3).toList());

        if (merged.isEmpty()) {
            merged.addAll(signals.qualifiedNames().stream().limit(4).toList());
        }

        return merged.stream().distinct().limit(6).toList();
    }
}