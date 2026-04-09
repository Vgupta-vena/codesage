package com.vena.codesage.graph.service.knowledge;

public record KnowledgeQuery(
        String projectKey,
        String query,
        int limit,
        boolean debug,
        boolean collapse,
        String entityType
) {
}
