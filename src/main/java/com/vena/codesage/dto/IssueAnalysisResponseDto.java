package com.vena.codesage.dto;

import java.util.List;

public record IssueAnalysisResponseDto(
        String problemStatement,
        String summary,
        List<KnowledgeResultItemDto> evidence,
        List<String> hypotheses,
        List<SuggestedChangeDto> suggestedChanges,
        List<CandidateChangeTargetDto> candidateChangeTargets,
        String traceSummary
) {
    public static IssueAnalysisResponseDto failure(String problemStatement, String summary, List<String> hypotheses, List<SuggestedChangeDto> suggestedChanges) {
        return new IssueAnalysisResponseDto(
                problemStatement,
                summary,
                List.of(),
                hypotheses,
                suggestedChanges,
                List.of(),
                "No trace available"
        );
    }
}