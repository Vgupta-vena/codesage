package com.vena.codesage.dto;

import java.util.List;

public record ExecutionPathDto(
        String category,
        int depth,
        List<String> nodes
) {}