package com.vena.codesage.dto;

import java.util.List;

public record CallPathDto(
        String startMethod,
        String endMethod,
        int depth,
        List<String> path,
        String category
) {}