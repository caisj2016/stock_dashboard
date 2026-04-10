package com.caisj.stockdashboard.backend.domain.model;

import java.util.List;

public record MacdSeries(
    List<Double> macd,
    List<Double> signal,
    List<Double> hist
) {
}
