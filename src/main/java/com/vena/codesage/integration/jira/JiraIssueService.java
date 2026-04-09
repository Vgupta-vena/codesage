package com.vena.codesage.integration.jira;

import org.springframework.stereotype.Service;

@Service
public class JiraIssueService {

    private final JiraClient jiraClient;

    public JiraIssueService(JiraClient jiraClient) {
        this.jiraClient = jiraClient;
    }

    public JiraIssue getIssue(String issueKey) {
        return jiraClient.getIssue(issueKey);
    }
}