package com.vena.codesage.dto;

import java.util.List;

public record ExecutionGraphDto(
        String rootMethod,
        List<ExecutionPathDto> paths,
        List<ExecutionPathDto> collapseCommonPrefixes
) {}