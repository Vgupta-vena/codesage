package com.vena.codesage.dto;

public record SmartSearchResultDto(
        String resultType,
        String title,
        String subtitle,
        String location,
        String preview,
        Integer score,
        String groupKey
) {
}