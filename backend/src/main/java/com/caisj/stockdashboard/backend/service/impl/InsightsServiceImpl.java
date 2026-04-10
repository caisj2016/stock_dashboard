package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.client.YahooFinanceClient;
import com.caisj.stockdashboard.backend.dto.response.ChartHistoryResponse;
import com.caisj.stockdashboard.backend.dto.response.CompanyProfileResponse;
import com.caisj.stockdashboard.backend.dto.response.InsightGroupResponse;
import com.caisj.stockdashboard.backend.dto.response.MetricItemResponse;
import com.caisj.stockdashboard.backend.dto.response.StockInsightsResponse;
import com.caisj.stockdashboard.backend.service.ChartService;
import com.caisj.stockdashboard.backend.service.InsightsService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class InsightsServiceImpl implements InsightsService {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter UPDATED_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final ChartService chartService;
    private final YahooFinanceClient yahooFinanceClient;

    public InsightsServiceImpl(ChartService chartService, YahooFinanceClient yahooFinanceClient) {
        this.chartService = chartService;
        this.yahooFinanceClient = yahooFinanceClient;
    }

    @Override
    @Cacheable(cacheNames = "stockInsights", key = "#symbol")
    public StockInsightsResponse getStockInsights(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        if (normalizedSymbol.isEmpty()) {
            return new StockInsightsResponse(false, normalizedSymbol, now(), "missing symbol", emptyProfile(), List.of());
        }

        ChartHistoryResponse chart = chartService.getChartHistory(normalizedSymbol, "D");
        if (!chart.ok()) {
            return new StockInsightsResponse(false, normalizedSymbol, now(), chart.error(), emptyProfile(), List.of());
        }

        JsonNode summary = loadQuoteSummary(normalizedSymbol);
        JsonNode profile = firstPresentNode(summary.path("summaryProfile"), summary.path("assetProfile"));
        JsonNode price = summary.path("price");
        JsonNode financial = summary.path("financialData");
        JsonNode stats = summary.path("defaultKeyStatistics");

        Double currentPrice = first(chart.price(), readNumber(price, "regularMarketPrice", "raw"));
        Double ma20 = last(chart.ma20());
        Double ma5 = last(chart.ma5());
        Double high20 = max(chart.closes(), 20);
        Double low20 = min(chart.closes(), 20);
        Double high60 = max(chart.closes(), 60);
        Double low60 = min(chart.closes(), 60);
        Double targetMean = readNumber(financial, "targetMeanPrice", "raw");
        Double targetLow = readNumber(financial, "targetLowPrice", "raw");
        Double targetHigh = readNumber(financial, "targetHighPrice", "raw");
        Double analystCount = readNumber(financial, "numberOfAnalystOpinions", "raw");
        Double revenueGrowth = pct(readNumber(financial, "revenueGrowth", "raw"));
        Double earningsGrowth = pct(readNumber(financial, "earningsGrowth", "raw"));
        Double grossMargins = pct(readNumber(financial, "grossMargins", "raw"));
        Double operatingMargins = pct(readNumber(financial, "operatingMargins", "raw"));
        Double forwardPe = readNumber(stats, "forwardPE", "raw");
        Double priceToBook = readNumber(stats, "priceToBook", "raw");
        boolean quoteSummaryAvailable = hasQuoteSummaryData(summary);

        return new StockInsightsResponse(
            true,
            normalizedSymbol,
            now(),
            null,
            buildCompanyProfile(price, profile),
            List.of(
                new InsightGroupResponse(
                    "Price Action",
                    "Recent trend, moving averages, and momentum.",
                    List.of(
                        metric("Last Price", fmt(currentPrice, 2), toneByCompare(currentPrice, ma20), "Latest chart price", null, currentPrice),
                        metric("MA5 / MA20", fmt(ma5, 2) + " / " + fmt(ma20, 2), toneByCompare(ma5, ma20), "Short-term versus medium-term trend", null, ma20),
                        metric("RSI14", fmt(chart.rsi14(), 2), toneByRsi(chart.rsi14()), "Below 35 may be oversold, above 70 may be overbought", null, chart.rsi14()),
                        metric("Volume Ratio", fmt(chart.volumeRatio(), 2) + "x", toneByThreshold(chart.volumeRatio(), 1.2, 0.8), "Latest volume versus recent average", null, chart.volumeRatio())
                    )
                ),
                new InsightGroupResponse(
                    "Trading Range",
                    "Where the latest price sits inside recent ranges.",
                    List.of(
                        metric("20D Range", fmt(low20, 2) + " - " + fmt(high20, 2), toneByZone(currentPrice, low20, high20), "Rolling 20-session range", null, currentPrice),
                        metric("60D Range", fmt(low60, 2) + " - " + fmt(high60, 2), toneByZone(currentPrice, low60, high60), "Rolling 60-session range", null, currentPrice),
                        metric("Daily Change", signedPct(chart.changePct()), toneByThreshold(chart.changePct(), 0.0, 0.0), "Latest percent change versus prior close", null, chart.changePct()),
                        metric("Symbol", normalizedSymbol, "neutral", "Ticker symbol used by the page", null, null)
                    )
                ),
                new InsightGroupResponse(
                    "Valuation",
                    quoteSummaryAvailable
                        ? "Derived from Yahoo Finance quoteSummary."
                        : "quoteSummary fields are limited right now; showing only available data.",
                    buildValuationItems(
                        currentPrice,
                        targetMean,
                        targetLow,
                        targetHigh,
                        analystCount,
                        revenueGrowth,
                        earningsGrowth,
                        grossMargins,
                        operatingMargins,
                        forwardPe,
                        priceToBook,
                        quoteSummaryAvailable
                    )
                )
            )
        );
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase();
    }

    private CompanyProfileResponse emptyProfile() {
        return new CompanyProfileResponse("--", "--", "Profile unavailable.", "Profile unavailable.");
    }

    private CompanyProfileResponse buildCompanyProfile(JsonNode price, JsonNode profile) {
        String sector = firstText(profile, "sectorDisp", "sector");
        String track = firstText(profile, "industryDisp", "industry");
        String business = buildBusinessText(price, profile, sector, track);
        String products = buildProductsText(price, profile, track, sector);
        return new CompanyProfileResponse(displayText(sector), displayText(track), business, products);
    }

    private String now() {
        return LocalDateTime.now(TOKYO).format(UPDATED_FORMATTER);
    }

    private MetricItemResponse metric(String label, String value, String tone, String detail, String help, Double numeric) {
        return new MetricItemResponse(label, value, tone, detail, help, numeric == null ? null : round(numeric, 2));
    }

    private List<MetricItemResponse> buildValuationItems(
        Double currentPrice,
        Double targetMean,
        Double targetLow,
        Double targetHigh,
        Double analystCount,
        Double revenueGrowth,
        Double earningsGrowth,
        Double grossMargins,
        Double operatingMargins,
        Double forwardPe,
        Double priceToBook,
        boolean quoteSummaryAvailable
    ) {
        List<MetricItemResponse> items = new ArrayList<>();
        if (targetMean != null) {
            items.add(metric("Target Mean", fmt(targetMean, 2), toneTarget(currentPrice, targetMean), "Consensus target price", null, targetMean));
        }
        if (targetLow != null || targetHigh != null) {
            items.add(metric("Target Range", fmt(targetLow, 2) + " - " + fmt(targetHigh, 2), "neutral", "Analyst target range", null, targetMean));
        }
        if (analystCount != null) {
            items.add(metric("Analysts", fmt(analystCount, 0), "neutral", "Number of analyst opinions", null, analystCount));
        }
        if (revenueGrowth != null) {
            items.add(metric("Revenue Growth", pctText(revenueGrowth), toneByThreshold(revenueGrowth, 8.0, 0.0), "Year-over-year revenue growth", null, revenueGrowth));
        }
        if (earningsGrowth != null) {
            items.add(metric("Earnings Growth", pctText(earningsGrowth), toneByThreshold(earningsGrowth, 8.0, 0.0), "Year-over-year earnings growth", null, earningsGrowth));
        }
        if (grossMargins != null || operatingMargins != null) {
            items.add(metric("Gross / Op Margin", pctText(grossMargins) + " / " + pctText(operatingMargins), "neutral", "Gross margin and operating margin", null, operatingMargins));
        }
        if (forwardPe != null) {
            items.add(metric("Forward PE", fmt(forwardPe, 2), "neutral", "Forward price-to-earnings", null, forwardPe));
        }
        if (priceToBook != null) {
            items.add(metric("P/B", fmt(priceToBook, 2), "neutral", "Price-to-book ratio", null, priceToBook));
        }
        if (!items.isEmpty()) {
            return items;
        }

        return List.of(metric(
            "Data Coverage",
            quoteSummaryAvailable ? "No valuation fields" : "Upstream unavailable",
            "neutral",
            quoteSummaryAvailable
                ? "quoteSummary responded, but these valuation fields were empty."
                : "Yahoo Finance quoteSummary could not provide valuation data for this request.",
            null,
            null
        ));
    }

    private Double last(List<Double> values) {
        return values == null || values.isEmpty() ? null : values.get(values.size() - 1);
    }

    private Double max(List<Double> values, int lookback) {
        if (values == null || values.isEmpty()) return null;
        int from = Math.max(0, values.size() - lookback);
        return values.subList(from, values.size()).stream().filter(v -> v != null).max(Double::compareTo).orElse(null);
    }

    private Double min(List<Double> values, int lookback) {
        if (values == null || values.isEmpty()) return null;
        int from = Math.max(0, values.size() - lookback);
        return values.subList(from, values.size()).stream().filter(v -> v != null).min(Double::compareTo).orElse(null);
    }

    private Double readNumber(JsonNode node, String field, String nested) {
        JsonNode valueNode = node.path(field);
        if (!nested.isBlank()) {
            valueNode = valueNode.path(nested);
        }
        return valueNode.isNumber() ? valueNode.asDouble() : null;
    }

    private JsonNode firstPresentNode(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && !node.isMissingNode() && !node.isNull() && node.size() > 0) {
                return node;
            }
        }
        return JsonNodeFactory.instance.objectNode();
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        for (String field : fields) {
            String value = node.path(field).asText("");
            if (value != null) {
                value = value.trim();
            }
            if (value != null && !value.isBlank() && !"--".equals(value)) {
                return value;
            }
        }
        return null;
    }

    private String displayText(String value) {
        return value == null || value.isBlank() ? "--" : value;
    }

    private String buildBusinessText(JsonNode price, JsonNode profile, String sector, String track) {
        String summary = firstText(profile, "longBusinessSummary", "description");
        if (summary != null) {
            return summary;
        }

        String name = firstText(price, "longName", "shortName");
        String country = firstText(profile, "country");
        String exchange = firstText(price, "exchangeName");
        List<String> descriptors = compactValues(sector, track, country, exchange);
        if (name != null && !descriptors.isEmpty()) {
            return name + " focuses on " + String.join(" / ", descriptors) + ".";
        }
        if (name != null) {
            return name + " is listed on Yahoo Finance, but the upstream profile summary is currently unavailable.";
        }
        if (!descriptors.isEmpty()) {
            return "Profile summary unavailable. Current coverage: " + String.join(" / ", descriptors) + ".";
        }
        return "Profile unavailable.";
    }

    private String buildProductsText(JsonNode price, JsonNode profile, String track, String sector) {
        String summary = firstText(profile, "longBusinessSummary", "description");
        String industry = firstText(profile, "industryDisp", "industry");
        List<String> descriptors = compactValues(track, industry, sector);
        if (!descriptors.isEmpty()) {
            return "Core focus: " + String.join(" / ", descriptors);
        }

        if (summary != null) {
            String sentence = firstSentence(summary);
            if (sentence != null) {
                return sentence;
            }
        }

        String exchange = firstText(price, "exchangeName");
        String quoteType = firstText(price, "quoteType");
        String country = firstText(profile, "country");
        descriptors = compactValues(exchange, quoteType, country);
        if (!descriptors.isEmpty()) {
            return "Coverage: " + String.join(" / ", descriptors);
        }
        return "Profile unavailable.";
    }

    private List<String> compactValues(String... values) {
        List<String> items = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank() || "--".equals(value) || items.contains(value)) {
                continue;
            }
            items.add(value);
        }
        return items;
    }

    private String firstSentence(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\n', ' ').trim();
        if (normalized.isBlank()) {
            return null;
        }
        int cut = normalized.length();
        for (char marker : new char[] {'.', ';'}) {
            int idx = normalized.indexOf(marker);
            if (idx > 0) {
                cut = Math.min(cut, idx + 1);
            }
        }
        String sentence = normalized.substring(0, Math.min(cut, 160)).trim();
        return sentence.isBlank() ? null : sentence;
    }

    private Double pct(Double value) {
        return value == null ? null : value * 100.0;
    }

    private boolean hasQuoteSummaryData(JsonNode summary) {
        return summary != null && summary.isObject() && summary.fieldNames().hasNext();
    }

    private String fmt(Double value, int scale) {
        if (value == null) return "--";
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String pctText(Double value) {
        return value == null ? "--" : fmt(value, 1) + "%";
    }

    private String signedPct(Double value) {
        if (value == null) return "--";
        return (value > 0 ? "+" : "") + fmt(value, 2) + "%";
    }

    private String toneByCompare(Double left, Double right) {
        if (left == null || right == null) return "neutral";
        if (left > right) return "up";
        if (left < right) return "down";
        return "neutral";
    }

    private String toneByRsi(Double rsi) {
        if (rsi == null) return "neutral";
        if (rsi <= 35) return "up";
        if (rsi >= 70) return "down";
        return "neutral";
    }

    private String toneByThreshold(Double value, double upperInclusive, double lowerInclusive) {
        if (value == null) return "neutral";
        if (value > upperInclusive) return "up";
        if (value < lowerInclusive) return "down";
        return "neutral";
    }

    private String toneByZone(Double price, Double low, Double high) {
        if (price == null || low == null || high == null || high <= low) return "neutral";
        double ratio = (price - low) / (high - low);
        if (ratio <= 0.33) return "up";
        if (ratio >= 0.67) return "down";
        return "neutral";
    }

    private String toneTarget(Double currentPrice, Double targetMean) {
        if (currentPrice == null || targetMean == null) return "neutral";
        if (targetMean > currentPrice) return "up";
        if (targetMean < currentPrice) return "down";
        return "neutral";
    }

    private Double first(Double preferred, Double fallback) {
        return preferred != null ? preferred : fallback;
    }

    private JsonNode loadQuoteSummary(String symbol) {
        try {
            return yahooFinanceClient.fetchQuoteSummary(
                symbol,
                List.of("price", "summaryProfile", "assetProfile", "financialData", "defaultKeyStatistics")
            );
        } catch (RuntimeException ex) {
            return JsonNodeFactory.instance.objectNode();
        }
    }

    private Double round(Double value, int scale) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
