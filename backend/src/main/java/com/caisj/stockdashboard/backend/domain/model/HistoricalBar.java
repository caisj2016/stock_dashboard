package com.caisj.stockdashboard.backend.domain.model;

public record HistoricalBar(
    long timestamp,
    Double open,
    Double high,
    Double low,
    Double close,
    Long volume
) {
}
