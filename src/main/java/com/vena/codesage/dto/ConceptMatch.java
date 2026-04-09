package com.vena.codesage.dto;

import java.util.List;

public record ConceptMatch (
        String concept,
        List<String> signals,
        int score
) {
}
