package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.client.YahooFinanceClient;
import com.caisj.stockdashboard.backend.dto.response.ChartHistoryResponse;
import com.caisj.stockdashboard.backend.dto.response.MetricItemResponse;
import com.caisj.stockdashboard.backend.dto.response.OwnershipCardResponse;
import com.caisj.stockdashboard.backend.dto.response.OwnershipShortResponse;
import com.caisj.stockdashboard.backend.service.ChartService;
import com.caisj.stockdashboard.backend.service.OwnershipService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class OwnershipServiceImpl implements OwnershipService {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter UPDATED_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ChartService chartService;
    private final YahooFinanceClient yahooFinanceClient;

    public OwnershipServiceImpl(ChartService chartService, YahooFinanceClient yahooFinanceClient) {
        this.chartService = chartService;
        this.yahooFinanceClient = yahooFinanceClient;
    }

    @Override
    @Cacheable(cacheNames = "ownershipShort", key = "#symbol")
    public OwnershipShortResponse getOwnershipShort(String symbol) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase();
        if (normalizedSymbol.isEmpty()) {
            return new OwnershipShortResponse(false, normalizedSymbol, "机构与空头", now(), "missing symbol", List.of());
        }

        ChartHistoryResponse chart = chartService.getChartHistory(normalizedSymbol, "D");
        JsonNode summary = loadQuoteSummary(normalizedSymbol);
        JsonNode stats = summary.path("defaultKeyStatistics");
        JsonNode financial = summary.path("financialData");

        Double instPct = toPercent(readNumber(stats, "heldPercentInstitutions", "raw"));
        Double insiderPct = toPercent(readNumber(stats, "heldPercentInsiders", "raw"));
        Double sharesShort = readNumber(stats, "sharesShort", "raw");
        Double shortPrior = readNumber(stats, "sharesShortPriorMonth", "raw");
        Double shortRatio = readNumber(financial, "shortRatio", "raw");
        Double shortPctFloat = toPercent(readNumber(stats, "shortPercentOfFloat", "raw"));
        Double sharesOutstanding = readNumber(stats, "sharesOutstanding", "raw");
        Double floatShares = readNumber(stats, "floatShares", "raw");

        Double avgVolume20 = average(chart.volumes(), 20);
        Double lastVolume = chart.volumes() == null || chart.volumes().isEmpty() ? null : chart.volumes().get(chart.volumes().size() - 1).doubleValue();
        Double daysToCover = (sharesShort != null && avgVolume20 != null && avgVolume20 > 0)
            ? Double.valueOf(sharesShort / avgVolume20)
            : shortRatio;

        return new OwnershipShortResponse(
            true,
            normalizedSymbol,
            "机构与空头",
            now(),
            null,
            List.of(
                new OwnershipCardResponse(
                    "ownership_mix",
                    "持仓结构",
                    "Yahoo Finance 默认统计口径",
                    List.of(
                        metric("机构持股", pctText(instPct), tone(instPct, 50.0, 20.0), "机构持股占总股本的比例", null, instPct),
                        metric("内部人持股", pctText(insiderPct), "neutral", "内部人持股占比", null, insiderPct),
                        metric("流通股", compactNumber(floatShares), "neutral", "可流通股数量", null, floatShares),
                        metric("总股本", compactNumber(sharesOutstanding), "neutral", "已发行股份数量", null, sharesOutstanding)
                    )
                ),
                new OwnershipCardResponse(
                    "short_interest",
                    "空头压力",
                    "用 short interest 与 days to cover 观察挤压风险",
                    List.of(
                        metric("Short Interest", compactNumber(sharesShort), tone(daysToCover, 5.0, 2.0), "当前空头股数", null, sharesShort),
                        metric("上月空头", compactNumber(shortPrior), "neutral", "上月 short interest", null, shortPrior),
                        metric("Short % Float", pctText(shortPctFloat), tone(shortPctFloat, 12.0, 5.0), "空头占流通股比例", null, shortPctFloat),
                        metric("Days to Cover", fmt(daysToCover, 2), tone(daysToCover, 5.0, 2.0), "空头股数 / 近 20 日均量", null, daysToCover)
                    )
                ),
                new OwnershipCardResponse(
                    "liquidity",
                    "流动性观察",
                    "结合图表页行情看换手承接",
                    List.of(
                        metric("最新成交量", compactNumber(lastVolume), tone(chart.volumeRatio(), 1.2, 0.8), "最近一根成交量", null, lastVolume),
                        metric("20 日均量", compactNumber(avgVolume20), "neutral", "近 20 根平均成交量", null, avgVolume20),
                        metric("量比", fmt(chart.volumeRatio(), 2) + "x", tone(chart.volumeRatio(), 1.2, 0.8), "最新成交量 / 近 5 根均量", null, chart.volumeRatio()),
                        metric("日涨跌幅", signedPct(chart.changePct()), tone(chart.changePct(), 0.0, 0.0), "最近一根价格变化", null, chart.changePct())
                    )
                )
            )
        );
    }

    private String now() {
        return LocalDateTime.now(TOKYO).format(UPDATED_FORMATTER);
    }

    private MetricItemResponse metric(String label, String value, String tone, String detail, String help, Double numeric) {
        return new MetricItemResponse(label, value, tone, detail, help, numeric == null ? null : round(numeric, 2));
    }

    private Double readNumber(JsonNode node, String field, String nested) {
        JsonNode valueNode = node.path(field);
        if (!nested.isBlank()) {
            valueNode = valueNode.path(nested);
        }
        return valueNode.isNumber() ? valueNode.asDouble() : null;
    }

    private Double toPercent(Double ratio) {
        return ratio == null ? null : ratio * 100.0;
    }

    private Double average(List<Long> values, int lookback) {
        if (values == null || values.isEmpty()) return null;
        int from = Math.max(0, values.size() - lookback);
        return values.subList(from, values.size()).stream().mapToLong(Long::longValue).average().orElse(Double.NaN);
    }

    private String compactNumber(Double value) {
        if (value == null) return "--";
        double abs = Math.abs(value);
        if (abs >= 100_000_000) return fmt(value / 100_000_000, 2) + "亿";
        if (abs >= 10_000) return fmt(value / 10_000, 1) + "万";
        return fmt(value, 0);
    }

    private String pctText(Double value) {
        return value == null ? "--" : fmt(value, 1) + "%";
    }

    private String signedPct(Double value) {
        if (value == null) return "--";
        return (value > 0 ? "+" : "") + fmt(value, 2) + "%";
    }

    private String fmt(Double value, int scale) {
        if (value == null) return "--";
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String tone(Double value, double upperInclusive, double lowerInclusive) {
        if (value == null) return "neutral";
        if (value > upperInclusive) return "up";
        if (value < lowerInclusive) return "down";
        return "neutral";
    }

    private JsonNode loadQuoteSummary(String symbol) {
        try {
            return yahooFinanceClient.fetchQuoteSummary(
                symbol,
                List.of("price", "defaultKeyStatistics", "financialData")
            );
        } catch (RuntimeException ex) {
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private Double round(Double value, int scale) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
