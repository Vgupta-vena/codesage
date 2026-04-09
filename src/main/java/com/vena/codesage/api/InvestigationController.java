package com.vena.codesage.api;

import com.vena.codesage.ai.IssueInvestigationService;
import com.vena.codesage.dto.IssueAnalysisResponseDto;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/investigation")
public class InvestigationController {

    private final IssueInvestigationService investigationService;

    public InvestigationController(IssueInvestigationService investigationService) {
        this.investigationService = investigationService;
    }

    @GetMapping
    public IssueAnalysisResponseDto investigate(
            @RequestParam String projectKey,
            @RequestParam(required = false) String problem,
            @RequestParam(required = false) String issueKey
    ) {
        String normalizedProblem = normalize(problem);
        String normalizedIssueKey = normalize(issueKey);

        if (hasText(normalizedIssueKey)) {
            return investigationService.investigateIssue(projectKey, normalizedIssueKey);
        }

        if (hasText(normalizedProblem)) {
            return investigationService.investigate(projectKey, normalizedProblem);
        }

        return new IssueAnalysisResponseDto(
                "",
                "Either problem or issueKey must be provided.",
                List.of(),
                List.of("Provide a free-text problem statement or a Jira issue key."),
                List.of(),
                List.of(),
                "No trace available"
        );
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}