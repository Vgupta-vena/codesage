package com.vena.codesage.dto;

import java.util.List;

public record SuggestedChangeDto(
        String suggestion,
        List<String> relatedTargets,
        List<String> reasons
) {
}