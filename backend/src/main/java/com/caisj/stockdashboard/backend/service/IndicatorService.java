package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.domain.model.MacdSeries;
import com.caisj.stockdashboard.backend.domain.model.MacdSnapshot;
import java.util.List;

public interface IndicatorService {
    Double calculateRsi(List<Double> closes, int period);

    Double simpleMovingAverage(List<Double> values, int period);

    List<Double> simpleMovingAverageSeries(List<Double> values, int period);

    MacdSnapshot calculateMacd(List<Double> closes);

    MacdSeries calculateMacdFullSeries(List<Double> closes);
}
