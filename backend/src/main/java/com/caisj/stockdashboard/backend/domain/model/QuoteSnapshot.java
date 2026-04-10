package com.caisj.stockdashboard.backend.domain.model;

import java.util.List;

public record QuoteSnapshot(
    String symbol,
    Double price,
    Double prevClose,
    Double change,
    Double pct,
    Long volume,
    List<Double> closes,
    String marketState,
    String updated
) {
}
