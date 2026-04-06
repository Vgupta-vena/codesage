package com.vena.codesage.graph.service.ranking;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class TokenNormalizer {

    public Set<String> tokenizeQuery(String value) {
        return tokenize(value);
    }

    public Set<String> tokenizeIdentifier(String value) {
        return tokenize(value);
    }

    public Set<String> tokenizeSection(String value) {
        return tokenize(value);
    }

    public String extractSimpleName(String qualifiedName) {
        if (qualifiedName == null || qualifiedName.isBlank()) {
            return "";
        }

        int lastDot = qualifiedName.lastIndexOf('.');
        return lastDot >= 0 ? qualifiedName.substring(lastDot + 1) : qualifiedName;
    }

    public boolean hasRealEntries(String section) {
        if (section == null || section.isBlank()) {
            return false;
        }

        String normalized = section.toLowerCase(Locale.ROOT);
        return !normalized.contains("- none");
    }

    public boolean containsTokensNearEachOther(String text, Set<String> tokens, int windowSize) {
        if (text == null || text.isBlank() || tokens == null || tokens.isEmpty()) {
            return false;
        }

        var words = Arrays.stream(text.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(word -> !word.isBlank())
                .toList();

        if (words.isEmpty()) {
            return false;
        }

        for (int start = 0; start < words.size(); start++) {
            int end = Math.min(words.size(), start + windowSize);
            Set<String> window = new LinkedHashSet<>(words.subList(start, end));
            if (window.containsAll(tokens)) {
                return true;
            }
        }

        return false;
    }

    private Set<String> tokenize(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }

        String normalized = value
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2")
                .replace('.', ' ')
                .replace('/', ' ')
                .replace('\\', ' ')
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('$', ' ')
                .toLowerCase(Locale.ROOT);

        return Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}