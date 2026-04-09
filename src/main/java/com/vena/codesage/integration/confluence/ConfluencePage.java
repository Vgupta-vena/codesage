package com.vena.codesage.integration.confluence;

public record ConfluencePage(
        String id,
        String title,
        String spaceKey,
        String webUrl,
        String excerpt,
        String content
) {
}