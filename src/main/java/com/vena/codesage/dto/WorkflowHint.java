package com.vena.codesage.dto;

import java.util.List;

public record WorkflowHint(
        String workflow,
        List<String> steps,
        List<String> entrySignals,
        int score
) {
}
