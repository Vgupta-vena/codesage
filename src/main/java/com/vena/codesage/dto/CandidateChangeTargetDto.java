package com.vena.codesage.dto;

import java.util.List;

public record CandidateChangeTargetDto(
        String target,
        int score,
        List<String> reasons
) {
}