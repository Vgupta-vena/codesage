package com.vena.codesage.dto;

import java.util.List;
import java.util.Map;

public record ProblemUnderstandingResult(
        String rawText,
        String normalizedText,
        List<ConceptMatch> concepts,
        List<WorkflowHint> workflows,
        List<String> candidateEntities,
        List<String> dandidateEndpoints,
        List<String> domainTerms,
        List<String> technicalTerms,
        Map<String, Object> metadata
) {
}
