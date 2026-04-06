package com.vena.codesage.graph.service.ranking;

import java.util.Set;

public record CandidateProfile(
        String qualifiedName,
        String simpleName,
        String filePath,
        String content,
        String summary,
        String touchpoints,
        String endpoints,
        String calledBy,
        Set<String> identifierTokens,
        Set<String> simpleNameTokens,
        Set<String> summaryTokens,
        Set<String> touchpointTokens,
        Set<String> endpointTokens,
        Set<String> calledByTokens,
        boolean hasRealTouchpoints,
        boolean hasRealEndpoints
) {
}