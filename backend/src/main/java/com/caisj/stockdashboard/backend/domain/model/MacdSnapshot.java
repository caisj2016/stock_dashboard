package com.caisj.stockdashboard.backend.domain.model;

import java.util.List;

public record MacdSnapshot(
    Double macd,
    Double signal,
    Double hist,
    boolean bullCross,
    List<Double> macdSeries,
    List<Double> signalSeries,
    List<Double> histSeries
) {
}
