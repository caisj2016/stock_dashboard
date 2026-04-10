package com.caisj.stockdashboard.backend.domain.model;

import java.util.List;

public record ScreenerMetrics(
    String symbol,
    String name,
    Double price,
    Double changePct,
    Double rsi14,
    Double macd,
    Double macdSignal,
    Double macdHist,
    boolean macdBullCross,
    Double volumeRatio,
    Double ma5,
    Double ma20,
    Double ma60,
    boolean maCross,
    boolean aboveMa20,
    boolean breakout20,
    List<Double> opens20,
    List<Double> highs20,
    List<Double> lows20,
    List<Double> closes20,
    List<Long> volumes20,
    List<Double> macdSeries,
    List<Double> signalSeries,
    List<Double> histSeries
) {
}
