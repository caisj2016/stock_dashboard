package com.caisj.stockdashboard.backend.domain.model;

public record ChartPoint(
    String date,
    long timestamp,
    Double open,
    Double high,
    Double low,
    Double close,
    Long volume
) {
}
