package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.domain.model.MacdSeries;
import com.caisj.stockdashboard.backend.domain.model.MacdSnapshot;
import com.caisj.stockdashboard.backend.service.impl.IndicatorServiceImpl;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndicatorServiceImplTests {

    private final IndicatorService indicatorService = new IndicatorServiceImpl();

    @Test
    void shouldMatchRsiReferenceValue() {
        List<Double> closes = List.of(
            44.34, 44.09, 44.15, 43.61, 44.33,
            44.83, 45.10, 45.42, 45.84, 46.08,
            45.89, 46.03, 45.61, 46.28, 46.28
        );

        Double rsi = indicatorService.calculateRsi(closes, 14);

        assertThat(rsi).isNotNull();
        assertThat(rsi).isCloseTo(70.4641, within(0.0001));
    }

    @Test
    void shouldAlignMacdSeriesToInputLength() {
        List<Double> closes = List.of(
            10.0, 10.2, 10.4, 10.1, 10.5, 10.7, 10.9, 11.0, 11.3, 11.5,
            11.6, 11.8, 11.7, 11.9, 12.2, 12.1, 12.4, 12.7, 12.9, 13.0,
            13.1, 13.3, 13.5, 13.6, 13.7, 13.9, 14.0, 14.2, 14.3, 14.5,
            14.7, 14.9, 15.0, 15.2, 15.4, 15.6, 15.8, 16.0, 16.3, 16.5
        );

        MacdSeries series = indicatorService.calculateMacdFullSeries(closes);
        MacdSnapshot snapshot = indicatorService.calculateMacd(closes);

        assertThat(series.macd()).hasSize(closes.size());
        assertThat(series.signal()).hasSize(closes.size());
        assertThat(series.hist()).hasSize(closes.size());
        assertThat(series.macd().get(0)).isNull();
        assertThat(snapshot.macd()).isNotNull();
        assertThat(snapshot.signal()).isNotNull();
        assertThat(snapshot.hist()).isEqualTo(snapshot.macd() - snapshot.signal());
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
