package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.client.YahooFinanceClient;
import com.caisj.stockdashboard.backend.domain.model.HistoricalBar;
import com.caisj.stockdashboard.backend.domain.model.MacdSnapshot;
import com.caisj.stockdashboard.backend.domain.model.PortfolioItemRecord;
import com.caisj.stockdashboard.backend.domain.model.ScreenerCandidate;
import com.caisj.stockdashboard.backend.domain.model.ScreenerMatch;
import com.caisj.stockdashboard.backend.domain.model.ScreenerMetrics;
import com.caisj.stockdashboard.backend.domain.model.ScreenerUniverseDefinition;
import com.caisj.stockdashboard.backend.dto.response.ScreenerResponse;
import com.caisj.stockdashboard.backend.repository.PortfolioRepository;
import com.caisj.stockdashboard.backend.service.IndicatorService;
import com.caisj.stockdashboard.backend.service.ScreenerService;
import com.caisj.stockdashboard.backend.service.ScreenerUniverseService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ScreenerServiceImpl implements ScreenerService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final YahooFinanceClient yahooFinanceClient;
    private final IndicatorService indicatorService;
    private final ScreenerUniverseService screenerUniverseService;
    private final PortfolioRepository portfolioRepository;

    public ScreenerServiceImpl(
        YahooFinanceClient yahooFinanceClient,
        IndicatorService indicatorService,
        ScreenerUniverseService screenerUniverseService,
        PortfolioRepository portfolioRepository
    ) {
        this.yahooFinanceClient = yahooFinanceClient;
        this.indicatorService = indicatorService;
        this.screenerUniverseService = screenerUniverseService;
        this.portfolioRepository = portfolioRepository;
    }

    @Override
    public ScreenerResponse getScreener(String mode, String universe, int limit) {
        ScreenerUniverseDefinition universeDefinition = screenerUniverseService.getUniverse(universe);
        Set<String> watchlistCodes = portfolioRepository.findAll().stream()
            .map(PortfolioItemRecord::code)
            .collect(Collectors.toSet());

        List<ScreenerMetrics> processedMetrics = universeDefinition.items().stream()
            .map(this::calculateMetrics)
            .filter(metrics -> metrics != null)
            .toList();

        List<ScreenerResponse.ScreenerItem> items = processedMetrics.stream()
            .map(metrics -> mapMatch(metrics, mode, watchlistCodes))
            .filter(item -> item != null)
            .sorted((left, right) -> compareItems(right, left))
            .limit(limit)
            .toList();

        return new ScreenerResponse(
            mode,
            universeDefinition.key(),
            universeDefinition.label(),
            universeDefinition.items().size(),
            universeDefinition.description(),
            LocalTime.now().format(TIME_FORMATTER),
            limit,
            items.size(),
            processedMetrics.isEmpty() ? "当前环境无法获取历史行情，请在可联网环境下刷新。" : "",
            items
        );
    }

    private ScreenerMetrics calculateMetrics(ScreenerCandidate candidate) {
        List<HistoricalBar> history = yahooFinanceClient.fetchHistory(candidate.code(), "6mo", "1d");
        if (history.size() < 35) {
            return null;
        }

        List<Double> opens = history.stream().map(HistoricalBar::open).toList();
        List<Double> highs = history.stream().map(HistoricalBar::high).toList();
        List<Double> lows = history.stream().map(HistoricalBar::low).toList();
        List<Double> closes = history.stream().map(HistoricalBar::close).toList();
        List<Long> volumes = history.stream().map(bar -> bar.volume() == null ? 0L : bar.volume()).toList();

        Double lastClose = closes.get(closes.size() - 1);
        Double prevClose = closes.size() >= 2 ? closes.get(closes.size() - 2) : null;
        Long lastVolume = volumes.get(volumes.size() - 1);
        Double avgVolume5 = averageLong(volumes, 5);
        Double ma5 = indicatorService.simpleMovingAverage(closes, 5);
        Double ma20 = indicatorService.simpleMovingAverage(closes, 20);
        Double ma60 = indicatorService.simpleMovingAverage(closes, 60);
        Double prevMa5 = indicatorService.simpleMovingAverage(closes.subList(0, closes.size() - 1), 5);
        Double prevMa20 = indicatorService.simpleMovingAverage(closes.subList(0, closes.size() - 1), 20);
        Double rsi14 = indicatorService.calculateRsi(closes, 14);
        MacdSnapshot macd = indicatorService.calculateMacd(closes);
        Double changePct = prevClose == null || prevClose == 0 ? null : ((lastClose - prevClose) / prevClose) * 100.0;
        Double volumeRatio = avgVolume5 == null || avgVolume5 == 0 ? null : lastVolume / avgVolume5;
        Double high20 = closes.size() >= 20 ? closes.subList(closes.size() - 20, closes.size()).stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN) : null;
        boolean breakout20 = high20 != null && lastClose >= high20;
        boolean maCross = prevMa5 != null && prevMa20 != null && ma5 != null && ma20 != null && prevMa5 <= prevMa20 && ma5 > ma20;
        boolean aboveMa20 = ma20 != null && lastClose > ma20;

        return new ScreenerMetrics(
            candidate.code(),
            candidate.name(),
            round(lastClose, 2),
            round(changePct, 2),
            round(rsi14, 2),
            round(macd.macd(), 4),
            round(macd.signal(), 4),
            round(macd.hist(), 4),
            macd.bullCross(),
            round(volumeRatio, 2),
            round(ma5, 2),
            round(ma20, 2),
            round(ma60, 2),
            maCross,
            aboveMa20,
            breakout20,
            tailRounded(opens, 20, 2),
            tailRounded(highs, 20, 2),
            tailRounded(lows, 20, 2),
            tailRounded(closes, 20, 2),
            tailLong(volumes, 20),
            tailRounded(macd.macdSeries(), 20, 4),
            tailRounded(macd.signalSeries(), 20, 4),
            tailRounded(macd.histSeries(), 20, 4)
        );
    }

    private ScreenerResponse.ScreenerItem mapMatch(ScreenerMetrics metrics, String mode, Set<String> watchlistCodes) {
        ScreenerMatch match = matchMode(metrics, mode);
        if (!match.matched()) {
            return null;
        }

        return new ScreenerResponse.ScreenerItem(
            metrics.symbol(),
            metrics.name(),
            metrics.price(),
            metrics.changePct(),
            metrics.rsi14(),
            metrics.macd(),
            metrics.macdSignal(),
            metrics.macdHist(),
            metrics.volumeRatio(),
            metrics.ma5(),
            metrics.ma20(),
            metrics.ma60(),
            metrics.macdBullCross(),
            metrics.maCross(),
            metrics.aboveMa20(),
            metrics.breakout20(),
            match.score(),
            watchlistCodes.contains(metrics.symbol()),
            metrics.opens20(),
            metrics.highs20(),
            metrics.lows20(),
            metrics.closes20(),
            metrics.volumes20(),
            metrics.macdSeries(),
            metrics.signalSeries(),
            metrics.histSeries(),
            match.signals()
        );
    }

    private ScreenerMatch matchMode(ScreenerMetrics metrics, String mode) {
        List<String> signals = new java.util.ArrayList<>();
        int score = 0;
        String normalizedMode = mode == null ? "combo" : mode.trim().toLowerCase();
        Double rsi14 = metrics.rsi14();
        Double changePct = metrics.changePct();
        double volumeRatio = metrics.volumeRatio() == null ? 0.0 : metrics.volumeRatio();

        if (rsi14 != null && rsi14 < 30) {
            signals.add("RSI " + rsi14);
            score += 18;
        }
        if (metrics.macdBullCross()) {
            signals.add("MACD 金叉");
            score += 16;
        }
        if (volumeRatio >= 1.8 && (changePct == null ? 0.0 : changePct) > 0) {
            signals.add("放量 " + round(volumeRatio, 2) + "x");
            score += 14;
        }
        if (metrics.maCross()) {
            signals.add("MA5 上穿 MA20");
            score += 12;
        }
        if (metrics.breakout20()) {
            signals.add("突破 20 日高点");
            score += 10;
        }
        if (metrics.aboveMa20()) {
            signals.add("站上 MA20");
            score += 6;
        }

        boolean matched;
        switch (normalizedMode) {
            case "oversold" -> matched = rsi14 != null && rsi14 < 30 && (changePct == null || changePct > -4);
            case "macd_cross" -> matched = metrics.macdBullCross() && volumeRatio >= 1.0;
            case "volume_breakout" -> matched = volumeRatio >= 1.8 && (changePct == null ? 0.0 : changePct) > 1.5;
            case "ma_breakout" -> matched = metrics.maCross() || metrics.breakout20();
            default -> matched = score >= 18 && signals.size() >= 2;
        }

        return new ScreenerMatch(matched, score, signals.stream().limit(3).toList());
    }

    private int compareItems(ScreenerResponse.ScreenerItem left, ScreenerResponse.ScreenerItem right) {
        int scoreCompare = Integer.compare(nullSafeInt(left.score()), nullSafeInt(right.score()));
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        int volumeCompare = Double.compare(nullSafeDouble(left.volumeRatio()), nullSafeDouble(right.volumeRatio()));
        if (volumeCompare != 0) {
            return volumeCompare;
        }
        return Double.compare(nullSafeDouble(left.changePct()), nullSafeDouble(right.changePct()));
    }

    private int nullSafeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private double nullSafeDouble(Double value) {
        return value == null ? 0.0 : value;
    }

    private Double averageLong(List<Long> values, int period) {
        if (values.size() < period) {
            return null;
        }
        long total = 0L;
        for (int i = values.size() - period; i < values.size(); i++) {
            total += values.get(i);
        }
        return total / (double) period;
    }

    private List<Double> tailRounded(List<Double> values, int size, int scale) {
        int fromIndex = Math.max(0, values.size() - size);
        return values.subList(fromIndex, values.size()).stream()
            .map(value -> round(value, scale))
            .toList();
    }

    private List<Long> tailLong(List<Long> values, int size) {
        int fromIndex = Math.max(0, values.size() - size);
        return values.subList(fromIndex, values.size());
    }

    private Double round(Double value, int scale) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
