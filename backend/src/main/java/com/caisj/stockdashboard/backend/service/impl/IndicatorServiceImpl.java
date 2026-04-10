package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.domain.model.MacdSeries;
import com.caisj.stockdashboard.backend.domain.model.MacdSnapshot;
import com.caisj.stockdashboard.backend.service.IndicatorService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class IndicatorServiceImpl implements IndicatorService {

    @Override
    public Double calculateRsi(List<Double> closes, int period) {
        if (closes == null || closes.size() < period + 1) {
            return null;
        }

        double totalGain = 0.0;
        double totalLoss = 0.0;

        for (int i = closes.size() - period; i < closes.size(); i++) {
            double current = closes.get(i);
            double previous = closes.get(i - 1);
            double delta = current - previous;
            totalGain += Math.max(delta, 0);
            totalLoss += Math.max(-delta, 0);
        }

        double avgGain = totalGain / period;
        double avgLoss = totalLoss / period;
        if (avgLoss == 0.0) {
            return 100.0;
        }

        double rs = avgGain / avgLoss;
        return 100 - (100 / (1 + rs));
    }

    @Override
    public Double simpleMovingAverage(List<Double> values, int period) {
        if (values == null || values.size() < period) {
            return null;
        }

        double total = 0.0;
        for (int i = values.size() - period; i < values.size(); i++) {
            total += values.get(i);
        }
        return total / period;
    }

    @Override
    public List<Double> simpleMovingAverageSeries(List<Double> values, int period) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<Double> result = new ArrayList<>(values.size());
        for (int i = 0; i < values.size(); i++) {
            if (i + 1 < period) {
                result.add(null);
                continue;
            }

            double total = 0.0;
            for (int j = i + 1 - period; j <= i; j++) {
                total += values.get(j);
            }
            result.add(total / period);
        }
        return result;
    }

    @Override
    public MacdSnapshot calculateMacd(List<Double> closes) {
        if (closes == null || closes.size() < 35) {
            return emptySnapshot();
        }

        List<Double> ema12 = ema(closes, 12);
        List<Double> ema26 = ema(closes, 26);
        if (ema12.isEmpty() || ema26.isEmpty()) {
            return emptySnapshot();
        }

        int pad = ema12.size() - ema26.size();
        List<Double> diffs = new ArrayList<>(ema26.size());
        for (int i = 0; i < ema26.size(); i++) {
            diffs.add(ema12.get(i + pad) - ema26.get(i));
        }

        List<Double> signal = ema(diffs, 9);
        if (signal.isEmpty()) {
            return emptySnapshot();
        }

        int pad2 = diffs.size() - signal.size();
        List<Double> histSeries = new ArrayList<>(signal.size());
        for (int i = 0; i < signal.size(); i++) {
            histSeries.add(diffs.get(i + pad2) - signal.get(i));
        }

        Double macdLine = lastOrNull(diffs);
        Double signalLine = lastOrNull(signal);
        Double prevMacd = diffs.size() >= 2 ? diffs.get(diffs.size() - 2) : null;
        Double prevSignal = signal.size() >= 2 ? signal.get(signal.size() - 2) : null;

        return new MacdSnapshot(
            macdLine,
            signalLine,
            macdLine != null && signalLine != null ? macdLine - signalLine : null,
            prevMacd != null && prevSignal != null && prevMacd <= prevSignal && macdLine != null && macdLine > signalLine,
            tail(diffs, 20),
            tail(signal, 20),
            tail(histSeries, 20)
        );
    }

    @Override
    public MacdSeries calculateMacdFullSeries(List<Double> closes) {
        int size = closes == null ? 0 : closes.size();
        if (closes == null || closes.size() < 35) {
            return emptySeries(size);
        }

        List<Double> ema12 = ema(closes, 12);
        List<Double> ema26 = ema(closes, 26);
        if (ema12.isEmpty() || ema26.isEmpty()) {
            return emptySeries(size);
        }

        int pad = ema12.size() - ema26.size();
        List<Double> diffs = new ArrayList<>(ema26.size());
        for (int i = 0; i < ema26.size(); i++) {
            diffs.add(ema12.get(i + pad) - ema26.get(i));
        }

        List<Double> signal = ema(diffs, 9);
        if (signal.isEmpty()) {
            return emptySeries(size);
        }

        int pad2 = diffs.size() - signal.size();
        List<Double> histValues = new ArrayList<>(signal.size());
        for (int i = 0; i < signal.size(); i++) {
            histValues.add(diffs.get(i + pad2) - signal.get(i));
        }

        return new MacdSeries(
            align(closes.size(), diffs),
            align(closes.size(), signal),
            align(closes.size(), histValues)
        );
    }

    private List<Double> ema(List<Double> values, int period) {
        if (values == null || values.size() < period) {
            return List.of();
        }

        double alpha = 2.0 / (period + 1);
        List<Double> emaValues = new ArrayList<>();
        double seed = 0.0;
        for (int i = 0; i < period; i++) {
            seed += values.get(i);
        }
        emaValues.add(seed / period);

        for (int i = period; i < values.size(); i++) {
            double previous = emaValues.get(emaValues.size() - 1);
            double current = (values.get(i) - previous) * alpha + previous;
            emaValues.add(current);
        }

        return emaValues;
    }

    private MacdSnapshot emptySnapshot() {
        return new MacdSnapshot(null, null, null, false, List.of(), List.of(), List.of());
    }

    private MacdSeries emptySeries(int size) {
        List<Double> blanks = new ArrayList<>(Collections.nCopies(size, null));
        return new MacdSeries(blanks, new ArrayList<>(blanks), new ArrayList<>(blanks));
    }

    private List<Double> align(int targetSize, List<Double> values) {
        List<Double> aligned = new ArrayList<>(Collections.nCopies(targetSize - values.size(), null));
        aligned.addAll(values);
        return aligned;
    }

    private Double lastOrNull(List<Double> values) {
        return values.isEmpty() ? null : values.get(values.size() - 1);
    }

    private List<Double> tail(List<Double> values, int size) {
        if (values.size() <= size) {
            return List.copyOf(values);
        }
        return List.copyOf(values.subList(values.size() - size, values.size()));
    }
}
