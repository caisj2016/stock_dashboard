package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.client.JapanKabukaClient;
import com.caisj.stockdashboard.backend.client.YahooFinanceClient;
import com.caisj.stockdashboard.backend.dto.response.ChartHistoryResponse;
import com.caisj.stockdashboard.backend.dto.response.OwnershipShortDebugResponse;
import com.caisj.stockdashboard.backend.service.ChartService;
import com.caisj.stockdashboard.backend.service.OwnershipDebugService;
import com.caisj.stockdashboard.backend.service.OwnershipService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class OwnershipDebugServiceImpl implements OwnershipDebugService {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    private final JapanKabukaClient japanKabukaClient;
    private final YahooFinanceClient yahooFinanceClient;
    private final ChartService chartService;
    private final OwnershipService ownershipService;

    public OwnershipDebugServiceImpl(
        JapanKabukaClient japanKabukaClient,
        YahooFinanceClient yahooFinanceClient,
        ChartService chartService,
        OwnershipService ownershipService
    ) {
        this.japanKabukaClient = japanKabukaClient;
        this.yahooFinanceClient = yahooFinanceClient;
        this.chartService = chartService;
        this.ownershipService = ownershipService;
    }

    @Override
    @Cacheable(cacheNames = "ownershipShortDebug", key = "#symbol")
    public OwnershipShortDebugResponse getOwnershipShortDebug(String symbol) {
        String normalizedSymbol = normalizeSymbol(symbol);
        if (normalizedSymbol.isBlank()) {
            return new OwnershipShortDebugResponse(
                false,
                normalizedSymbol,
                isJapanSymbol(normalizedSymbol) ? "jp" : "global",
                now(),
                Map.of(),
                Map.of(),
                Map.of(),
                "missing symbol",
                List.of("symbol"),
                null
            );
        }
        return isJapanSymbol(normalizedSymbol) ? buildJapanDebug(normalizedSymbol) : buildGlobalDebug(normalizedSymbol);
    }

    private OwnershipShortDebugResponse buildJapanDebug(String symbol) {
        String overviewUrl = japanKabukaClient.buildOverviewUrl(symbol);
        String detailUrl = japanKabukaClient.buildDetailUrl(symbol);

        Map<String, Object> source = new LinkedHashMap<>();
        source.put("provider", "japan-kabuka.com");
        source.put("overviewUrl", overviewUrl);
        source.put("detailUrl", detailUrl);
        source.put("overviewFetchOk", false);
        source.put("detailFetchOk", false);
        source.put("overviewError", "");
        source.put("detailError", "");

        String overviewHtml = "";
        String detailHtml = "";
        try {
            overviewHtml = japanKabukaClient.fetchOverviewHtml(symbol);
            source.put("overviewFetchOk", true);
        } catch (RuntimeException ex) {
            source.put("overviewError", ex.getMessage());
        }
        try {
            detailHtml = japanKabukaClient.fetchDetailHtml(symbol);
            source.put("detailFetchOk", true);
        } catch (RuntimeException ex) {
            source.put("detailError", ex.getMessage());
        }

        List<List<String>> overviewRows = extractRowsById(overviewHtml, "myTable");
        List<List<String>> detailRows = extractRowsById(detailHtml, "myTable");
        List<List<String>> institutionRows = extractRowsByClass(detailHtml, "kikann-table");

        Map<String, String> overviewParsed = parseJapanOverview(overviewRows);
        Map<String, String> detailParsed = parseJapanDetail(detailRows, institutionRows);

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("overviewLatestRow", latestDataRow(overviewRows));
        raw.put("detailLatestRow", latestDataRow(detailRows));
        raw.put("overviewRecentRows", recentDataRows(overviewRows));
        raw.put("detailRecentRows", recentDataRows(detailRows));
        raw.put("institutionListPreview", institutionPreview(institutionRows));
        raw.put("overviewParsed", overviewParsed);
        raw.put("detailParsed", detailParsed);

        Map<String, Boolean> zeroLikeMap = new LinkedHashMap<>();
        for (String key : List.of("creditSell", "creditBuy", "jsfSellBalance", "jsfBuyBalance", "shortageShares", "reverseFee")) {
            String value = overviewParsed.get(key);
            if (value != null) {
                zeroLikeMap.put(key, isZeroLikeText(value));
            }
        }

        Map<String, String> requiredFields = new LinkedHashMap<>();
        requiredFields.put("date", overviewParsed.get("date"));
        requiredFields.put("institutionDelta", overviewParsed.get("institutionDelta"));
        requiredFields.put("creditSell", overviewParsed.get("creditSell"));
        requiredFields.put("creditBuy", overviewParsed.get("creditBuy"));
        requiredFields.put("jsfSellBalance", overviewParsed.get("jsfSellBalance"));
        requiredFields.put("jsfBuyBalance", overviewParsed.get("jsfBuyBalance"));
        requiredFields.put("shortageShares", overviewParsed.get("shortageShares"));
        requiredFields.put("reverseFee", overviewParsed.get("reverseFee"));
        requiredFields.put("institutionCount", detailParsed.get("institutionCount"));

        List<String> missingFields = new ArrayList<>();
        for (Map.Entry<String, String> entry : requiredFields.entrySet()) {
            if (normalizeText(entry.getValue()).isBlank()) {
                missingFields.add(entry.getKey());
            }
        }

        Map<String, Object> derived = new LinkedHashMap<>();
        derived.put("overviewRowCells", ((List<?>) raw.get("overviewLatestRow")).size());
        derived.put("detailRowCells", ((List<?>) raw.get("detailLatestRow")).size());
        derived.put("institutionListCount", Math.max(0, institutionRows.size() - 1));
        derived.put("overviewZeroLikeMap", zeroLikeMap);
        derived.put("allBalanceFieldsZeroLike", !zeroLikeMap.isEmpty() && zeroLikeMap.values().stream().allMatch(Boolean::booleanValue));
        derived.put("hasParsedOverview", !overviewParsed.isEmpty());
        derived.put("hasParsedDetail", !detailParsed.isEmpty());
        derived.put("finalPayloadProvider", "japan-kabuka.com");

        String diagnosis;
        if (!String.valueOf(source.get("overviewError")).isBlank() || !String.valueOf(source.get("detailError")).isBlank()) {
            diagnosis = "Source page fetch failed. Check overviewError / detailError first.";
        } else if (overviewParsed.isEmpty() && detailParsed.isEmpty()) {
            diagnosis = "Pages were fetched, but no target table could be parsed. Parsing rules likely need adjustment.";
        } else if (Boolean.TRUE.equals(derived.get("allBalanceFieldsZeroLike"))) {
            diagnosis = "Source rows were parsed, but the latest balance fields are effectively zero-like.";
        } else if (!missingFields.isEmpty()) {
            diagnosis = "Some fields were parsed, but there are still gaps in the source snapshot.";
        } else {
            diagnosis = "Japan Kabuka fields are available and should be enough for the page and debugging view.";
        }

        return new OwnershipShortDebugResponse(
            true,
            symbol,
            "jp",
            now(),
            source,
            raw,
            derived,
            diagnosis,
            missingFields,
            ownershipService.getOwnershipShort(symbol)
        );
    }

    private OwnershipShortDebugResponse buildGlobalDebug(String symbol) {
        ChartHistoryResponse chart = chartService.getChartHistory(symbol, "D");
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("infoError", "");

        JsonNode summary;
        try {
            summary = yahooFinanceClient.fetchQuoteSummary(symbol, List.of("defaultKeyStatistics", "financialData"));
        } catch (RuntimeException ex) {
            summary = JsonNodeFactory.instance.objectNode();
            source.put("infoError", ex.getMessage());
        }

        JsonNode stats = summary.path("defaultKeyStatistics");
        JsonNode financial = summary.path("financialData");
        Double heldPercentInstitutions = percentage(readNumber(stats, "heldPercentInstitutions", "raw"));
        Double sharesShort = readNumber(stats, "sharesShort", "raw");
        Double sharesShortPriorMonth = readNumber(stats, "sharesShortPriorMonth", "raw");
        Double shortRatio = readNumber(financial, "shortRatio", "raw");
        Double shortPercentOfFloat = percentage(readNumber(stats, "shortPercentOfFloat", "raw"));
        Double sharesOutstanding = readNumber(stats, "sharesOutstanding", "raw");
        Double floatShares = readNumber(stats, "floatShares", "raw");

        List<Long> volumes = chart.volumes() == null ? List.of() : chart.volumes();
        Double avgVolume30 = average(volumes, 30);
        Double denominator = floatShares != null ? floatShares : sharesOutstanding;
        Double computedShortPercent = shortPercentOfFloat;
        if (computedShortPercent == null && sharesShort != null && denominator != null && denominator > 0) {
            computedShortPercent = round((sharesShort / denominator) * 100.0, 1);
        }

        Double computedShortRatio = shortRatio;
        if (computedShortRatio == null && sharesShort != null && avgVolume30 != null && avgVolume30 > 0) {
            computedShortRatio = round(sharesShort / avgVolume30, 1);
        }

        Double shortMonthDeltaPct = null;
        if (sharesShort != null && sharesShortPriorMonth != null && sharesShortPriorMonth != 0) {
            shortMonthDeltaPct = round(((sharesShort - sharesShortPriorMonth) / sharesShortPriorMonth) * 100.0, 1);
        }

        Map<String, Object> raw = new LinkedHashMap<>();
        raw.put("heldPercentInstitutions", heldPercentInstitutions);
        raw.put("sharesShort", sharesShort);
        raw.put("sharesShortPriorMonth", sharesShortPriorMonth);
        raw.put("shortRatio", shortRatio);
        raw.put("shortPercentOfFloat", shortPercentOfFloat);
        raw.put("sharesOutstanding", sharesOutstanding);
        raw.put("floatShares", floatShares);

        Map<String, Object> derived = new LinkedHashMap<>();
        derived.put("avgVolume30", avgVolume30);
        derived.put("denominatorShares", denominator);
        derived.put("shortPercentComputed", computedShortPercent);
        derived.put("shortRatioComputed", computedShortRatio);
        derived.put("shortMonthDeltaPct", shortMonthDeltaPct);
        derived.put("hasShortCoverage", computedShortPercent != null || computedShortRatio != null);

        Map<String, Object> chartMap = new LinkedHashMap<>();
        chartMap.put("ok", chart.ok());
        chartMap.put("error", chart.error());
        chartMap.put("points", chart.closes() == null ? 0 : chart.closes().size());
        raw.put("chart", chartMap);

        List<String> missingFields = new ArrayList<>();
        if (sharesShort == null) missingFields.add("sharesShort");
        if (shortRatio == null) missingFields.add("shortRatio");
        if (shortPercentOfFloat == null) missingFields.add("shortPercentOfFloat");
        if (floatShares == null && sharesOutstanding == null) missingFields.add("floatShares/sharesOutstanding");

        String diagnosis;
        if (!String.valueOf(source.get("infoError")).isBlank()) {
            diagnosis = "Quote summary request failed. Check Yahoo Finance availability first.";
        } else if (sharesShort == null && shortRatio == null && shortPercentOfFloat == null) {
            diagnosis = "Yahoo returned no short-interest fields for this symbol.";
        } else if (computedShortPercent == null && denominator == null) {
            diagnosis = "Short-interest data exists, but float or shares outstanding is missing.";
        } else if (computedShortRatio == null && avgVolume30 == null) {
            diagnosis = "Short-interest data exists, but history volume is missing for days-to-cover.";
        } else if (!chart.ok()) {
            diagnosis = "Short-interest fields are present, but chart history failed and will block the final card.";
        } else if (Boolean.TRUE.equals(derived.get("hasShortCoverage"))) {
            diagnosis = "Short-interest fields are available and the final ownership card should render.";
        } else {
            diagnosis = "Only a partial short-interest snapshot is available.";
        }

        return new OwnershipShortDebugResponse(
            true,
            symbol,
            "global",
            now(),
            source,
            raw,
            derived,
            diagnosis,
            missingFields,
            ownershipService.getOwnershipShort(symbol)
        );
    }

    private List<List<String>> extractRowsById(String html, String tableId) {
        if (html == null || html.isBlank()) return List.of();
        Document document = Jsoup.parse(html);
        Element table = document.getElementById(tableId);
        return extractRows(table);
    }

    private List<List<String>> extractRowsByClass(String html, String tableClass) {
        if (html == null || html.isBlank()) return List.of();
        Document document = Jsoup.parse(html);
        Element table = document.selectFirst("table." + tableClass);
        return extractRows(table);
    }

    private List<List<String>> extractRows(Element table) {
        if (table == null) return List.of();
        List<List<String>> rows = new ArrayList<>();
        for (Element row : table.select("tr")) {
            List<String> cells = new ArrayList<>();
            for (Element cell : row.select("th,td")) {
                cells.add(normalizeText(cell.text()));
            }
            if (cells.stream().anyMatch(value -> !value.isBlank())) {
                rows.add(cells);
            }
        }
        return rows;
    }

    private Map<String, String> parseJapanOverview(List<List<String>> rows) {
        if (rows.size() < 3) return Map.of();
        List<String> latest = rows.get(2);
        if (latest.size() < 10) return Map.of();
        Map<String, String> parsed = new LinkedHashMap<>();
        parsed.put("date", latest.get(0));
        parsed.put("institutionDelta", latest.get(2));
        parsed.put("creditSell", latest.get(3));
        parsed.put("creditBuy", latest.get(4));
        parsed.put("jsfSellBalance", latest.get(5));
        parsed.put("jsfBuyBalance", latest.get(6));
        parsed.put("rotationDays", latest.get(7));
        parsed.put("shortageShares", latest.get(8));
        parsed.put("reverseFee", latest.get(9));
        return parsed;
    }

    private Map<String, String> parseJapanDetail(List<List<String>> detailRows, List<List<String>> institutionRows) {
        Map<String, String> parsed = new LinkedHashMap<>();
        if (institutionRows.size() >= 2) {
            parsed.put("institutionCount", String.valueOf(Math.max(0, institutionRows.size() - 1)));
        }
        if (detailRows.size() >= 3) {
            List<String> latest = detailRows.get(2);
            if (latest.size() >= 6) {
                parsed.put("detailDate", latest.get(0));
                parsed.put("institutionTotalDelta", latest.get(latest.size() - 3));
                parsed.put("institutionSellTotal", latest.get(latest.size() - 2));
                parsed.put("institutionBuyTotal", latest.get(latest.size() - 1));
            }
        }
        return parsed;
    }

    private List<String> latestDataRow(List<List<String>> rows) {
        return rows.size() >= 3 ? rows.get(2) : List.of();
    }

    private List<List<String>> recentDataRows(List<List<String>> rows) {
        if (rows.size() < 3) return List.of();
        int toIndex = Math.min(rows.size(), 10);
        return rows.subList(2, toIndex);
    }

    private List<List<String>> institutionPreview(List<List<String>> rows) {
        if (rows.size() < 2) return List.of();
        int toIndex = Math.min(rows.size(), 6);
        return rows.subList(1, toIndex);
    }

    private String normalizeSymbol(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase();
    }

    private boolean isJapanSymbol(String symbol) {
        return symbol != null && symbol.endsWith(".T") && symbol.length() > 2;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.replace('\u00a0', ' ').replace("\r", " ").replace("\n", " ").trim().replaceAll("\\s+", " ");
    }

    private boolean isZeroLikeText(String value) {
        String text = normalizeText(value);
        if (text.isBlank() || "-".equals(text)) {
            return true;
        }
        String compact = text
            .replace(" ", "")
            .replace("株", "")
            .replace("円", "")
            .replace("日", "")
            .replace("倍", "")
            .replace("%", "")
            .replace("(", "")
            .replace(")", "")
            .replace("+", "")
            .replace("-", "")
            .replace(".", "")
            .replace(",", "");
        return compact.isBlank() || compact.chars().allMatch(ch -> ch == '0');
    }

    private String now() {
        return OffsetDateTime.now(TOKYO).toString();
    }

    private Double readNumber(JsonNode node, String field, String nested) {
        JsonNode valueNode = node.path(field);
        if (nested != null && !nested.isBlank()) {
            valueNode = valueNode.path(nested);
        }
        return valueNode.isNumber() ? valueNode.asDouble() : null;
    }

    private Double percentage(Double ratio) {
        return ratio == null ? null : round(ratio * 100.0, 1);
    }

    private Double average(List<Long> values, int lookback) {
        if (values == null || values.isEmpty()) return null;
        int from = Math.max(0, values.size() - lookback);
        return values.subList(from, values.size()).stream().mapToLong(Long::longValue).average().orElse(Double.NaN);
    }

    private Double round(Double value, int scale) {
        if (value == null) return null;
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
