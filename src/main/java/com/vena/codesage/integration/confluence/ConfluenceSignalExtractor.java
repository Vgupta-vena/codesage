package com.vena.codesage.integration.confluence;

import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ConfluenceSignalExtractor {

    private static final Pattern ENDPOINT_PATTERN =
            Pattern.compile("(?<!\\w)(/(?:[a-zA-Z0-9._{}-]+(?:/[a-zA-Z0-9._{}-]+)*)/?)(?!\\w)");

    private static final Pattern CLASS_NAME_PATTERN =
            Pattern.compile("(?<![\\w.$])([A-Z][a-zA-Z0-9]*(?:Service|Controller|Repository|Client|Manager|Handler|Facade|Mapper|Processor|Orchestrator|Config|Util|Helper))(?![\\w$])");

    private static final Pattern QUALIFIED_NAME_PATTERN =
            Pattern.compile("(?<![\\w$])([a-z][a-z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*){2,})(?![\\w$])");

    public ExtractedSignals extract(String text) {
        if (text == null || text.isBlank()) {
            return new ExtractedSignals(List.of(), List.of(), List.of());
        }

        Set<String> endpoints = extractMatches(text, ENDPOINT_PATTERN, 8, 120);
        Set<String> classNames = extractMatches(text, CLASS_NAME_PATTERN, 12, 80);
        Set<String> qualifiedNames = extractMatches(text, QUALIFIED_NAME_PATTERN, 12, 180);

        return new ExtractedSignals(
                List.copyOf(endpoints),
                List.copyOf(classNames),
                List.copyOf(qualifiedNames)
        );
    }

    private Set<String> extractMatches(String text, Pattern pattern, int maxItems, int maxLength) {
        Matcher matcher = pattern.matcher(text);
        Set<String> matches = new LinkedHashSet<>();

        while (matcher.find() && matches.size() < maxItems) {
            String value = matcher.group(1);
            if (value == null) {
                continue;
            }

            String normalized = normalize(value);
            if (!normalized.isBlank() && normalized.length() <= maxLength) {
                matches.add(normalized);
            }
        }

        return matches;
    }

    private String normalize(String value) {
        String normalized = value.trim();

        while (!normalized.isEmpty() && isTrailingNoise(normalized.charAt(normalized.length() - 1))) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private boolean isTrailingNoise(char ch) {
        return ch == '.' || ch == ',' || ch == ';' || ch == ':' || ch == ')' || ch == ']' || ch == '}';
    }

    public record ExtractedSignals(
            List<String> endpoints,
            List<String> classNames,
            List<String> qualifiedNames
    ) {
        public boolean isEmpty() {
            return endpoints.isEmpty() && classNames.isEmpty() && qualifiedNames.isEmpty();
        }
    }
}