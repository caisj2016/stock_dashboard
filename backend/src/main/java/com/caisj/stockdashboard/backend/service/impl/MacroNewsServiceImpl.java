package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.client.RssFeedClient;
import com.caisj.stockdashboard.backend.dto.response.TopicDigestItemResponse;
import com.caisj.stockdashboard.backend.dto.response.TopicDigestResponse;
import com.caisj.stockdashboard.backend.dto.response.TrumpNewsItemResponse;
import com.caisj.stockdashboard.backend.service.MacroNewsService;
import com.caisj.stockdashboard.backend.util.LocalizationUtils;
import java.net.URI;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class MacroNewsServiceImpl implements MacroNewsService {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter UPDATED_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private static final List<String> TRUMP_FEEDS = List.of(
        "https://news.google.com/rss/search?q=Trump+OR+%22White+House%22+OR+tariff&hl=en-US&gl=US&ceid=US:en",
        "https://feeds.reuters.com/reuters/politicsNews",
        "https://feeds.apnews.com/rss/apf-politics",
        "https://thehill.com/rss/syndicator/19110",
        "http://feeds.bbci.co.uk/news/world/us_and_canada/rss.xml"
    );

    private static final Map<String, List<String>> TOPIC_FEEDS = Map.of(
        "nikkei", List.of(
            "https://feeds.reuters.com/reuters/businessNews",
            "https://feeds.reuters.com/reuters/worldNews",
            "https://feeds.apnews.com/rss/apf-business",
            "http://feeds.bbci.co.uk/news/business/rss.xml",
            "https://www.cnbc.com/id/100003114/device/rss/rss.html"
        ),
        "semiconductor", List.of(
            "https://feeds.reuters.com/reuters/technologyNews",
            "https://feeds.reuters.com/reuters/businessNews",
            "https://feeds.apnews.com/rss/apf-business",
            "http://feeds.bbci.co.uk/news/technology/rss.xml",
            "https://www.cnbc.com/id/19854910/device/rss/rss.html"
        )
    );

    private final RssFeedClient rssFeedClient;

    public MacroNewsServiceImpl(RssFeedClient rssFeedClient) {
        this.rssFeedClient = rssFeedClient;
    }

    @Override
    @Cacheable(cacheNames = "stockNews", key = "'trump'")
    public List<TrumpNewsItemResponse> getTrumpNews() {
        List<TrumpNewsItemResponse> preferred = dedupeTrumpItems(
            TRUMP_FEEDS.stream()
                .flatMap(feed -> safeFetch(feed).stream()
                    .filter(this::matchesTrump)
                    .map(entry -> toTrumpItem(feed, entry)))
                .sorted(Comparator.comparing(TrumpNewsItemResponse::pub, Comparator.nullsLast(String::compareTo)).reversed())
                .toList()
        );
        if (!preferred.isEmpty()) {
            return preferred.stream().limit(8).toList();
        }

        List<TrumpNewsItemResponse> fallback = dedupeTrumpItems(
            TRUMP_FEEDS.stream()
                .flatMap(feed -> safeFetch(feed).stream()
                    .limit(3)
                    .map(entry -> toTrumpItem(feed, entry)))
                .sorted(Comparator.comparing(TrumpNewsItemResponse::pub, Comparator.nullsLast(String::compareTo)).reversed())
                .toList()
        );
        return fallback.stream().limit(8).toList();
    }

    @Override
    @Cacheable(cacheNames = "topicDigest", key = "#topic")
    public TopicDigestResponse getTopicDigest(String topic) {
        String normalizedTopic = topic == null ? "" : topic.trim().toLowerCase(Locale.ROOT);
        List<String> feeds = TOPIC_FEEDS.getOrDefault(normalizedTopic, TOPIC_FEEDS.get("nikkei"));
        List<FeedItem> items = feeds.stream()
            .flatMap(feed -> safeFetch(feed).stream().map(entry -> toFeedItem(feed, entry)))
            .filter(item -> matchesTopic(normalizedTopic, item))
            .sorted(Comparator.comparing(FeedItem::publishedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
            .limit(6)
            .toList();

        List<String> drivers = collectDrivers(items, normalizedTopic);
        String toneClass = deriveTone(items);
        String tone = switch (toneClass) {
            case "up" -> "偏多";
            case "down" -> "偏空";
            default -> "中性";
        };

        return new TopicDigestResponse(
            normalizedTopic.isBlank() ? "nikkei" : normalizedTopic,
            tone,
            toneClass,
            buildSummary(normalizedTopic, tone, drivers, items.size()),
            LocalTime.now(TOKYO).format(UPDATED_FORMATTER),
            drivers,
            items.stream()
                .limit(3)
                .map(item -> new TopicDigestItemResponse(
                    item.provider(),
                    item.pub(),
                    item.title(),
                    LocalizationUtils.localizeHeadline(item.title()),
                    summarizeToChinese(item.title(), item.description(), normalizedTopic),
                    item.url()
                ))
                .toList()
        );
    }

    private List<RssFeedClient.RssEntry> safeFetch(String feed) {
        try {
            return rssFeedClient.fetch(feed);
        } catch (RuntimeException ex) {
            return List.of();
        }
    }

    private List<TrumpNewsItemResponse> dedupeTrumpItems(List<TrumpNewsItemResponse> items) {
        Map<String, TrumpNewsItemResponse> unique = new LinkedHashMap<>();
        for (TrumpNewsItemResponse item : items) {
            String key = normalizeDedupKey(item.title(), item.url());
            unique.putIfAbsent(key, item);
        }
        return new ArrayList<>(unique.values());
    }

    private String normalizeDedupKey(String title, String url) {
        String base = (title == null ? "" : title) + "|" + (url == null ? "" : url);
        return base.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private boolean matchesTrump(RssFeedClient.RssEntry entry) {
        String haystack = ((entry.title() == null ? "" : entry.title()) + " " +
            (entry.description() == null ? "" : entry.description())).toLowerCase(Locale.ROOT);
        return containsAny(
            haystack,
            "trump",
            "donald trump",
            "president trump",
            "white house",
            "tariff",
            "trade war",
            "reciprocal tariff",
            "u.s. administration",
            "us administration"
        );
    }

    private boolean matchesTopic(String topic, FeedItem item) {
        String haystack = (item.title() + " " + item.description()).toLowerCase(Locale.ROOT);
        if ("semiconductor".equals(topic)) {
            return containsAny(haystack, "chip", "semiconductor", "ai", "tsmc", "nvidia", "hbm", "gpu");
        }
        return containsAny(haystack, "japan", "nikkei", "tokyo", "yen", "toyota", "bank of japan", "boj");
    }

    private boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private TrumpNewsItemResponse toTrumpItem(String feed, RssFeedClient.RssEntry entry) {
        String source = detectSource(feed);
        String titleZh = LocalizationUtils.localizeHeadline(entry.title());
        String summaryZh = summarizeTrumpItem(entry.title(), entry.description());
        return new TrumpNewsItemResponse(
            source,
            formatPub(entry.pubDate()),
            LocalizationUtils.cleanText(entry.title()),
            titleZh,
            summaryZh,
            summaryZh.isBlank() ? titleZh : summaryZh,
            entry.url(),
            detectMarketTags(entry)
        );
    }

    private FeedItem toFeedItem(String feed, RssFeedClient.RssEntry entry) {
        OffsetDateTime publishedAt = rssFeedClient.parsePubDate(entry.pubDate());
        return new FeedItem(
            detectSource(feed),
            LocalizationUtils.cleanText(entry.title()),
            LocalizationUtils.cleanText(entry.description()),
            entry.url(),
            publishedAt,
            formatPub(entry.pubDate())
        );
    }

    private String formatPub(String raw) {
        OffsetDateTime parsed = rssFeedClient.parsePubDate(raw);
        if (parsed == null) {
            return raw == null ? "" : raw;
        }
        return parsed.atZoneSameInstant(TOKYO).format(DISPLAY_FORMATTER);
    }

    private String detectSource(String feed) {
        String host = URI.create(feed).getHost();
        if (host == null) {
            return "NEWS";
        }
        if (host.contains("reuters")) return "REUTERS";
        if (host.contains("apnews")) return "APNEWS";
        if (host.contains("thehill")) return "THEHILL";
        if (host.contains("bbc")) return "BBC";
        if (host.contains("cnbc")) return "CNBC";
        if (host.contains("google")) return "GOOGLE";
        return Jsoup.parse(host).text().toUpperCase(Locale.ROOT);
    }

    private List<String> detectMarketTags(RssFeedClient.RssEntry entry) {
        String haystack = (LocalizationUtils.cleanText(entry.title()) + " " +
            LocalizationUtils.cleanText(entry.description())).toLowerCase(Locale.ROOT);
        List<String> tags = new ArrayList<>();
        if (haystack.contains("tariff")) tags.add("关税");
        if (haystack.contains("trade")) tags.add("贸易");
        if (haystack.contains("china")) tags.add("中国");
        if (haystack.contains("fed")) tags.add("美联储");
        if (haystack.contains("election")) tags.add("选举");
        if (haystack.contains("tax")) tags.add("税务");
        if (haystack.contains("tech")) tags.add("科技");
        return tags;
    }

    private List<String> collectDrivers(List<FeedItem> items, String topic) {
        Set<String> drivers = new LinkedHashSet<>();
        for (FeedItem item : items) {
            String haystack = (item.title() + " " + item.description()).toLowerCase(Locale.ROOT);
            if (haystack.contains("tariff")) drivers.add("关税扰动");
            if (haystack.contains("yen")) drivers.add("日元波动");
            if (containsAny(haystack, "japan", "bank of japan", "boj")) drivers.add("日本市场");
            if (haystack.contains("toyota")) drivers.add("汽车权重");
            if (containsAny(haystack, "chip", "semiconductor", "hbm")) drivers.add("半导体链");
            if (haystack.contains("ai")) drivers.add("AI 需求预期");
            if (drivers.size() >= 3) break;
        }
        if (drivers.isEmpty()) {
            drivers.add("semiconductor".equals(topic) ? "科技板块" : "日本市场");
        }
        return new ArrayList<>(drivers);
    }

    private String deriveTone(List<FeedItem> items) {
        String joined = items.stream()
            .map(item -> (item.title() + " " + item.description()).toLowerCase(Locale.ROOT))
            .collect(Collectors.joining(" "));
        if (containsAny(joined, "surge", "rise", "growth", "lead", "record high", "boost")) {
            return "up";
        }
        if (containsAny(joined, "risk", "fall", "pressure", "weak", "fine", "angry")) {
            return "down";
        }
        return "neutral";
    }

    private String buildSummary(String topic, String tone, List<String> drivers, int count) {
        String label = "semiconductor".equals(topic) ? "半导体与科技板块" : "日本市场";
        String reason = drivers.isEmpty() ? "市场消息" : String.join("、", drivers);
        return label + "当前整体偏" + tone + "，本次整理了 " + count + " 条相关新闻，核心关注点包括 " + reason + "。";
    }

    private String summarizeTrumpItem(String title, String description) {
        String combined = (LocalizationUtils.cleanText(title) + " " +
            LocalizationUtils.cleanText(description)).toLowerCase(Locale.ROOT);
        if (combined.contains("tariff")) {
            return "关税相关表态再起，市场会重新评估贸易摩擦对通胀、企业利润和风险偏好的影响。";
        }
        if (combined.contains("big tech") && combined.contains("fine")) {
            return "欧美对大型科技公司的监管和罚款继续升级，科技板块估值承压。";
        }
        if (combined.contains("white house")) {
            return "白宫最新政策与表态继续影响全球风险偏好，短线情绪波动可能加大。";
        }
        if (combined.contains("trade")) {
            return "贸易议题升温会影响全球供应链和资本市场风险偏好，需关注后续政策细节。";
        }
        return summarizeToChinese(title, description, "trump");
    }

    private String summarizeToChinese(String title, String description, String topic) {
        String combined = (LocalizationUtils.cleanText(title) + " " +
            LocalizationUtils.cleanText(description)).toLowerCase(Locale.ROOT);
        if ("nikkei".equals(topic)) {
            if (combined.contains("fast retailing") && combined.contains("profit forecast")) {
                return "迅销上调盈利预期后股价走强，日本消费与龙头权重板块情绪受提振。";
            }
            if (combined.contains("yen")) {
                return "日元相关波动仍是日股的重要变量，出口和权重板块情绪容易受到影响。";
            }
            if (containsAny(combined, "japan", "bank of japan", "boj")) {
                return "新闻焦点集中在日本市场与政策预期，短线会继续影响权重股和指数表现。";
            }
        }
        if ("semiconductor".equals(topic)) {
            if (combined.contains("ai model") || combined.contains("ai")) {
                return "AI 相关进展继续强化算力与半导体需求预期，科技板块情绪偏积极。";
            }
            if (containsAny(combined, "chip", "semiconductor", "tsmc", "hbm", "gpu")) {
                return "芯片与半导体链条仍是科技主线，供需与资本开支预期持续受到关注。";
            }
        }
        if (combined.contains("openai") && combined.contains("anthropic")) {
            return "OpenAI 与 Anthropic 的融资和竞争动态，反映 AI 产业景气度仍在提升。";
        }
        if (combined.contains("alibaba") && combined.contains("investment")) {
            return "阿里继续加码 AI 投资，说明大厂仍在积极推进模型与应用落地。";
        }
        if (combined.contains("record high")) {
            return "相关资产创出新高，说明资金风险偏好改善，板块情绪偏强。";
        }
        return fallbackChineseSummary(title, description);
    }

    private String fallbackChineseSummary(String title, String description) {
        String cleanTitle = LocalizationUtils.localizeHeadline(title);
        String cleanDesc = LocalizationUtils.cleanText(description);
        if (cleanDesc.isBlank()) {
            return cleanTitle;
        }
        String localizedDesc = LocalizationUtils.localizeFinancialText(cleanDesc);
        localizedDesc = shorten(localizedDesc, 60);
        return localizedDesc.isBlank() ? cleanTitle : localizedDesc;
    }

    private String shorten(String value, int max) {
        String text = LocalizationUtils.cleanText(value);
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "...";
    }

    private record FeedItem(
        String provider,
        String title,
        String description,
        String url,
        OffsetDateTime publishedAt,
        String pub
    ) {
    }
}
