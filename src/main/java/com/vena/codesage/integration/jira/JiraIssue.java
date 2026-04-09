package com.vena.codesage.integration.jira;

public record JiraIssue(
        String key,
        String summary,
        String description,
        String issueType,
        String status,
        String priority,
        String assignee,
        String webUrl
) {
    public String problemStatement() {
        StringBuilder sb = new StringBuilder();

        if (summary != null && !summary.isBlank()) {
            sb.append(summary.trim());
        }

        if (description != null && !description.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append(description.trim());
        }

        return sb.toString().trim();
    }
}