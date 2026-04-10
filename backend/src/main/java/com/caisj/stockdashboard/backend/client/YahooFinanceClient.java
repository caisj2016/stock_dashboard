package com.caisj.stockdashboard.backend.client;

import com.caisj.stockdashboard.backend.config.AppProperties;
import com.caisj.stockdashboard.backend.domain.model.HistoricalBar;
import com.caisj.stockdashboard.backend.domain.model.QuoteSnapshot;
import com.caisj.stockdashboard.backend.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

@Component
public class YahooFinanceClient {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public YahooFinanceClient(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(appProperties.getMarketData().getTimeoutSeconds()))
            .build();
    }

    public List<HistoricalBar> fetchHistory(String symbol, String range, String interval) {
        String body = sendChartRequest(symbol, range, interval);
        try {
            return parseHistory(body);
        } catch (IOException ex) {
            throw new ApiException("UPSTREAM_ERROR", "Failed to parse market history: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    public QuoteSnapshot fetchQuote(String symbol) {
        String body = sendChartRequest(symbol, "5d", "30m");
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode result = root.path("chart").path("result");
            if (!result.isArray() || result.isEmpty()) {
                return new QuoteSnapshot(symbol, null, null, null, null, 0L, List.of(), "CLOSED", LocalDateTime.now(TOKYO).format(TIME_FORMATTER));
            }

            JsonNode first = result.get(0);
            JsonNode meta = first.path("meta");
            JsonNode quoteNode = first.path("indicators").path("quote").isArray() && !first.path("indicators").path("quote").isEmpty()
                ? first.path("indicators").path("quote").get(0)
                : objectMapper.createObjectNode();

            Double price = numberOrNull(meta.path("regularMarketPrice"));
            Double prevClose = numberOrNull(meta.path("previousClose"));
            Double change = price != null && prevClose != null ? round(price - prevClose, 2) : null;
            Double pct = change != null && prevClose != null && prevClose != 0 ? round(change / prevClose * 100.0, 2) : null;
            Long volume = longOrNull(meta.path("regularMarketVolume"));
            List<Double> closes = parseCloseSeries(quoteNode.path("close"));
            if (price == null && !closes.isEmpty()) {
                price = closes.get(closes.size() - 1);
            }

            return new QuoteSnapshot(
                symbol,
                price,
                prevClose,
                change,
                pct,
                volume == null ? 0L : volume,
                closes,
                meta.path("marketState").asText("CLOSED"),
                LocalDateTime.now(TOKYO).format(TIME_FORMATTER)
            );
        } catch (IOException ex) {
            throw new ApiException("UPSTREAM_ERROR", "Failed to parse quote response: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    public JsonNode fetchQuoteSummary(String symbol, List<String> modules) {
        String moduleQuery = String.join(",", modules);
        String url = "https://query2.finance.yahoo.com/v10/finance/quoteSummary/"
            + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
            + "?modules="
            + URLEncoder.encode(moduleQuery, StandardCharsets.UTF_8)
            + "&lang=en-US&region=US";
        String body = sendGet(url);
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode result = root.path("quoteSummary").path("result");
            if (!result.isArray() || result.isEmpty()) {
                return objectMapper.createObjectNode();
            }
            return result.get(0);
        } catch (IOException ex) {
            throw new ApiException("UPSTREAM_ERROR", "Failed to parse quote summary: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    public List<NewsRssItem> fetchStockNewsRss(String symbol) {
        String url = "https://feeds.finance.yahoo.com/rss/2.0/headline?s="
            + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
            + "&region=US&lang=en-US";
        String body = sendGet(url);
        Document document = Jsoup.parse(body, "", org.jsoup.parser.Parser.xmlParser());
        List<NewsRssItem> items = new ArrayList<>();
        for (Element item : document.select("item")) {
            items.add(new NewsRssItem(
                item.selectFirst("title") != null ? item.selectFirst("title").text() : "",
                item.selectFirst("description") != null ? item.selectFirst("description").text() : "",
                item.selectFirst("pubDate") != null ? item.selectFirst("pubDate").text() : "",
                item.selectFirst("link") != null ? item.selectFirst("link").text() : ""
            ));
            if (items.size() >= 8) {
                break;
            }
        }
        return items;
    }

    private String sendChartRequest(String symbol, String range, String interval) {
        String url = appProperties.getMarketData().getYahooChartBaseUrl()
            + "/"
            + URLEncoder.encode(symbol, StandardCharsets.UTF_8)
            + "?range="
            + URLEncoder.encode(range, StandardCharsets.UTF_8)
            + "&interval="
            + URLEncoder.encode(interval, StandardCharsets.UTF_8)
            + "&includePrePost=false&events=div%2Csplits";

        return sendGet(url);
    }

    private String sendGet(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(appProperties.getMarketData().getTimeoutSeconds()))
            .header("User-Agent", "stock-dashboard-refactor/1.0")
            .GET()
            .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new ApiException("UPSTREAM_ERROR", "Yahoo Finance request failed with status " + response.statusCode(), HttpStatus.BAD_GATEWAY);
            }
            return response.body();
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException("UPSTREAM_ERROR", "Failed to fetch market history: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    public record NewsRssItem(
        String title,
        String summary,
        String pubDate,
        String url
    ) {
    }

    private List<HistoricalBar> parseHistory(String body) throws IOException {
        JsonNode root = objectMapper.readTree(body);
        JsonNode result = root.path("chart").path("result");
        if (!result.isArray() || result.isEmpty()) {
            return List.of();
        }

        JsonNode first = result.get(0);
        JsonNode timestamps = first.path("timestamp");
        JsonNode quote = first.path("indicators").path("quote");
        if (!timestamps.isArray() || !quote.isArray() || quote.isEmpty()) {
            return List.of();
        }

        JsonNode quoteNode = quote.get(0);
        JsonNode opens = quoteNode.path("open");
        JsonNode highs = quoteNode.path("high");
        JsonNode lows = quoteNode.path("low");
        JsonNode closes = quoteNode.path("close");
        JsonNode volumes = quoteNode.path("volume");

        List<HistoricalBar> bars = new ArrayList<>();
        for (int i = 0; i < timestamps.size(); i++) {
            Double open = numberOrNull(opens.get(i));
            Double high = numberOrNull(highs.get(i));
            Double low = numberOrNull(lows.get(i));
            Double close = numberOrNull(closes.get(i));
            if (open == null || high == null || low == null || close == null) {
                continue;
            }

            long timestamp = timestamps.get(i).asLong() * 1000L;
            Long volume = longOrNull(volumes.get(i));
            bars.add(new HistoricalBar(timestamp, open, high, low, close, volume));
        }
        return bars;
    }

    private Double numberOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asDouble();
    }

    private Long longOrNull(JsonNode node) {
        if (node == null || node.isNull() || !node.isNumber()) {
            return null;
        }
        return node.asLong();
    }

    private List<Double> parseCloseSeries(JsonNode closesNode) {
        if (closesNode == null || !closesNode.isArray()) {
            return List.of();
        }
        List<Double> closes = new ArrayList<>();
        for (JsonNode node : closesNode) {
            Double value = numberOrNull(node);
            if (value != null) {
                closes.add(round(value, 2));
            }
        }
        int from = Math.max(0, closes.size() - 40);
        return closes.subList(from, closes.size());
    }

    private Double round(Double value, int scale) {
        if (value == null) {
            return null;
        }
        return java.math.BigDecimal.valueOf(value).setScale(scale, java.math.RoundingMode.HALF_UP).doubleValue();
    }
}
