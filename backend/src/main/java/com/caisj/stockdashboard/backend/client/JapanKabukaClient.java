package com.caisj.stockdashboard.backend.client;

import com.caisj.stockdashboard.backend.config.AppProperties;
import com.caisj.stockdashboard.backend.exception.ApiException;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class JapanKabukaClient {

    private static final Pattern CHARSET_PATTERN = Pattern.compile("(?i)charset\\s*=\\s*['\"]?([a-z0-9_\\-]+)");

    private final HttpClient httpClient;
    private final AppProperties appProperties;

    public JapanKabukaClient(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(appProperties.getMarketData().getTimeoutSeconds()))
            .build();
    }

    public String buildOverviewUrl(String symbol) {
        return "https://japan-kabuka.com/gyakuhibuChart/?id=" + encode(symbolCode(symbol));
    }

    public String buildDetailUrl(String symbol) {
        return "https://japan-kabuka.com/chart_detail?id=" + encode(symbolCode(symbol));
    }

    public String fetchOverviewHtml(String symbol) {
        return fetchPage(buildOverviewUrl(symbol));
    }

    public String fetchDetailHtml(String symbol) {
        return fetchPage(buildDetailUrl(symbol));
    }

    private String fetchPage(String url) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(appProperties.getMarketData().getTimeoutSeconds()))
            .header("User-Agent", "stock-dashboard-refactor/1.0")
            .header("Accept-Language", "ja,en-US;q=0.9,en;q=0.8")
            .GET()
            .build();
        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() >= 400) {
                throw new ApiException("UPSTREAM_ERROR", "Japan Kabuka request failed with status " + response.statusCode(), HttpStatus.BAD_GATEWAY);
            }
            return decodeBody(response);
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new ApiException("UPSTREAM_ERROR", "Failed to fetch Japan Kabuka page: " + ex.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    private String symbolCode(String symbol) {
        String text = symbol == null ? "" : symbol.trim().toUpperCase();
        return text.endsWith(".T") ? text.substring(0, text.length() - 2) : text;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String decodeBody(HttpResponse<byte[]> response) {
        byte[] body = response.body();
        Charset charset = detectCharset(response, body);
        return new String(body, charset);
    }

    private Charset detectCharset(HttpResponse<byte[]> response, byte[] body) {
        String headerCharset = response.headers()
            .firstValue("Content-Type")
            .map(this::extractCharset)
            .orElse("");
        Charset resolved = resolveCharset(headerCharset);
        if (resolved != null) {
            return resolved;
        }

        String headSnippet = new String(body, StandardCharsets.ISO_8859_1);
        Matcher matcher = CHARSET_PATTERN.matcher(headSnippet);
        if (matcher.find()) {
            resolved = resolveCharset(matcher.group(1));
            if (resolved != null) {
                return resolved;
            }
        }

        for (String candidate : List.of("Windows-31J", "Shift_JIS", "EUC-JP", "UTF-8")) {
            resolved = resolveCharset(candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        return StandardCharsets.UTF_8;
    }

    private String extractCharset(String contentType) {
        Matcher matcher = CHARSET_PATTERN.matcher(contentType == null ? "" : contentType);
        return matcher.find() ? matcher.group(1) : "";
    }

    private Charset resolveCharset(String raw) {
        String normalized = raw == null ? "" : raw.trim().replace("\"", "").replace("'", "");
        if (normalized.isBlank()) {
            return null;
        }
        String upper = normalized.toUpperCase(Locale.ROOT);
        if ("SHIFT-JIS".equals(upper) || "SHIFT_JIS".equals(upper) || "SJIS".equals(upper) || "CP932".equals(upper)) {
            normalized = "Windows-31J";
        }
        try {
            return Charset.forName(normalized);
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
