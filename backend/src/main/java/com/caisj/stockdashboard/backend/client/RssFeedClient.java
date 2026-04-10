package com.caisj.stockdashboard.backend.client;

import com.caisj.stockdashboard.backend.config.AppProperties;
import com.caisj.stockdashboard.backend.exception.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class RssFeedClient {

    private final HttpClient httpClient;
    private final AppProperties appProperties;

    public RssFeedClient(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(appProperties.getMarketData().getTimeoutSeconds()))
            .build();
    }

    public List<RssEntry> fetch(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(appProperties.getMarketData().getTimeoutSeconds()))
            .header("User-Agent", "stock-dashboard-refactor/1.0")
            .GET()
            .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 400) {
                throw new ApiException("UPSTREAM_ERROR", "RSS request failed with status " + response.statusCode(), HttpStatus.BAD_GATEWAY);
            }
            Document document = Jsoup.parse(response.body(), "", org.jsoup.parser.Parser.xmlParser());
            List<RssEntry> entries = new ArrayList<>();
            for (Element item : document.select("item")) {
                entries.add(new RssEntry(
                    text(item, "title"),
                    text(item, "description"),
                    text(item, "pubDate"),
                    text(item, "link")
                ));
            }
            return entries;
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException("UPSTREAM_ERROR", "Failed to fetch RSS feed: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    public OffsetDateTime parsePubDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String text(Element item, String selector) {
        Element node = item.selectFirst(selector);
        return node == null ? "" : node.text();
    }

    public record RssEntry(
        String title,
        String description,
        String pubDate,
        String url
    ) {
    }
}
