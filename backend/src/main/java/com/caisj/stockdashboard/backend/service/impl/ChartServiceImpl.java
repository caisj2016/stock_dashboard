package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.client.YahooFinanceClient;
import com.caisj.stockdashboard.backend.domain.model.ChartIntervalConfig;
import com.caisj.stockdashboard.backend.domain.model.ChartPoint;
import com.caisj.stockdashboard.backend.domain.model.HistoricalBar;
import com.caisj.stockdashboard.backend.domain.model.MacdSeries;
import com.caisj.stockdashboard.backend.dto.response.ChartHistoryResponse;
import com.caisj.stockdashboard.backend.service.ChartIntervalService;
import com.caisj.stockdashboard.backend.service.ChartService;
import com.caisj.stockdashboard.backend.service.IndicatorService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ChartServiceImpl implements ChartService {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter UPDATED_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final YahooFinanceClient yahooFinanceClient;
    private final IndicatorService indicatorService;
    private final ChartIntervalService chartIntervalService;

    public ChartServiceImpl(
        YahooFinanceClient yahooFinanceClient,
        IndicatorService indicatorService,
        ChartIntervalService chartIntervalService
    ) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.indicatorService = indicatorService;
        this.chartIntervalService = chartIntervalService;
    }

    @Override
    public ChartHistoryResponse getChartHistory(String symbol, String interval) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase();
        ChartIntervalConfig config = chartIntervalService.getConfig(interval);
        if (normalizedSymbol.isEmpty()) {
            return errorResponse(normalizedSymbol, config, "missing symbol");
        }

        List<HistoricalBar> history = yahooFinanceClient.fetchHistory(normalizedSymbol, config.range(), config.fetchInterval());
        if (history.isEmpty()) {
            return errorResponse(normalizedSymbol, config, "当前环境无法获取历史行情，请稍后重试。");
        }

        List<ChartPoint> rows = history.stream()
            .map(bar -> toChartPoint(bar, config.fetchInterval()))
            .toList();
        if (config.groupSize() != null) {
            rows = groupOhlcv(rows, config.groupSize());
        }
        rows = compressOhlcvRows(rows, config.limit());

        if (rows.size() < 12) {
            return errorResponse(normalizedSymbol, config, "可用历史数据不足，暂时无法绘制图表。");
        }

        List<Double> closes = rows.stream().map(ChartPoint::close).toList();
        List<Long> volumes = rows.stream().map(ChartPoint::volume).toList();
        List<Double> ma5 = indicatorService.simpleMovingAverageSeries(closes, 5);
        List<Double> ma20 = indicatorService.simpleMovingAverageSeries(closes, 20);
        MacdSeries macdSeries = indicatorService.calculateMacdFullSeries(closes);
        Double rsi14 = indicatorService.calculateRsi(closes, 14);
        Double prevClose = closes.size() >= 2 ? closes.get(closes.size() - 2) : null;
        Double latestClose = closes.get(closes.size() - 1);
        Double changePct = prevClose == null || prevClose == 0 ? null : ((latestClose - prevClose) / prevClose) * 100.0;
        Double avgVolume5 = averageVolume(volumes, 5);
        Double volumeRatio = avgVolume5 == null || avgVolume5 == 0 ? null : volumes.get(volumes.size() - 1) / avgVolume5;

        return new ChartHistoryResponse(
            true,
            normalizedSymbol,
            normalizedSymbol,
            config.key(),
            config.label(),
            null,
            rows.stream().map(ChartPoint::date).toList(),
            rows.stream().map(ChartPoint::timestamp).toList(),
            rounded(rows.stream().map(ChartPoint::open).toList(), 2),
            rounded(rows.stream().map(ChartPoint::high).toList(), 2),
            rounded(rows.stream().map(ChartPoint::low).toList(), 2),
            rounded(closes, 2),
            volumes,
            rounded(ma5, 2),
            rounded(ma20, 2),
            rounded(macdSeries.macd(), 4),
            rounded(macdSeries.signal(), 4),
            rounded(macdSeries.hist(), 4),
            round(latestClose, 2),
            round(changePct, 2),
            round(rsi14, 2),
            round(volumeRatio, 2),
            LocalDateTime.now(TOKYO).format(UPDATED_FORMATTER)
        );
    }

    private ChartHistoryResponse errorResponse(String symbol, ChartIntervalConfig config, String error) {
        return new ChartHistoryResponse(
            false,
            symbol,
            symbol,
            config.key(),
            config.label(),
            error,
            List.<String>of(),
            List.<Long>of(),
            List.<Double>of(),
            List.<Double>of(),
            List.<Double>of(),
            List.<Double>of(),
            List.<Long>of(),
            List.<Double>of(),
            List.<Double>of(),
            List.<Double>of(),
            List.<Double>of(),
            List.<Double>of(),
            null,
            null,
            null,
            null,
            null
        );
    }

    private ChartPoint toChartPoint(HistoricalBar bar, String fetchInterval) {
        LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(bar.timestamp()), TOKYO);
        String dateLabel = switch (fetchInterval) {
            case "1d", "1wk", "1mo" -> dateTime.format(DATE_FORMATTER);
            default -> dateTime.format(DATETIME_FORMATTER);
        };

        return new ChartPoint(
            dateLabel,
            bar.timestamp(),
            round(bar.open(), 2),
            round(bar.high(), 2),
            round(bar.low(), 2),
            round(bar.close(), 2),
            bar.volume() == null ? 0L : bar.volume()
        );
    }

    private List<ChartPoint> groupOhlcv(List<ChartPoint> rows, int chunkSize) {
        List<ChartPoint> grouped = new ArrayList<>();
        for (int i = 0; i < rows.size(); i += chunkSize) {
            List<ChartPoint> chunk = rows.subList(i, Math.min(rows.size(), i + chunkSize));
            if (chunk.isEmpty()) {
                continue;
            }
            ChartPoint first = chunk.get(0);
            ChartPoint last = chunk.get(chunk.size() - 1);
            double high = chunk.stream().mapToDouble(ChartPoint::high).max().orElse(last.high());
            double low = chunk.stream().mapToDouble(ChartPoint::low).min().orElse(last.low());
            long volume = chunk.stream().mapToLong(ChartPoint::volume).sum();
            grouped.add(new ChartPoint(last.date(), last.timestamp(), first.open(), high, low, last.close(), volume));
        }
        return grouped;
    }

    private List<ChartPoint> compressOhlcvRows(List<ChartPoint> rows, int limit) {
        if (rows.size() <= limit) {
            return rows;
        }
        int chunkSize = Math.max(2, rows.size() / limit + (rows.size() % limit == 0 ? 0 : 1));
        return groupOhlcv(rows, chunkSize);
    }

    private Double averageVolume(List<Long> values, int period) {
        if (values.size() < period) {
            return null;
        }
        long total = 0L;
        for (int i = values.size() - period; i < values.size(); i++) {
            total += values.get(i);
        }
        return total / (double) period;
    }

    private List<Double> rounded(List<Double> values, int scale) {
        return values.stream().map(value -> round(value, scale)).toList();
    }

    private Double round(Double value, int scale) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
