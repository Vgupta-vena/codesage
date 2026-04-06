package com.vena.codesage.graph.service.ranking;

import java.util.Map;

public record RankingExplanation(
        Map<String, Object> query,
        Map<String, Object> candidate,
        Map<String, Object> matches,
        Map<String, Object> signals,
        Map<String, Object> penalties,
        int finalScore
) {
}