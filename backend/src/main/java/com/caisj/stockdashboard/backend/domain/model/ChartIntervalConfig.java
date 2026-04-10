package com.caisj.stockdashboard.backend.domain.model;

public record ChartIntervalConfig(
    String key,
    String range,
    String fetchInterval,
    String label,
    int limit,
    Integer groupSize
) {
}
