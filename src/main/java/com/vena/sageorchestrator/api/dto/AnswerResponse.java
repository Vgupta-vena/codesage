package com.vena.sageorchestrator.api.dto;

import java.util.List;

public record AnswerResponse(
        String answer,
        String confidence,
        List<MatchedEntity> matchedEntities,
        List<EvidenceItem> evidence,
        List<String> gaps
) {
    public record MatchedEntity(
            String type,
            String value
    ) {}

    public record EvidenceItem(
            String source,
            String type,
            String title,
            String location
    ) {}
}
