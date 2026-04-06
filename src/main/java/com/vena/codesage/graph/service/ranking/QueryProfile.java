package com.vena.codesage.graph.service.ranking;

import java.util.List;
import java.util.Set;

public record QueryProfile(
        String raw,
        List<String> primaryTokens,
        List<String> expandedTokens,
        Set<String> actionTokens,
        Set<String> objectTokens
) {}