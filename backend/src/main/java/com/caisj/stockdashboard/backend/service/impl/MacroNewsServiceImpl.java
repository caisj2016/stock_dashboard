package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.client.RssFeedClient;
import com.caisj.stockdashboard.backend.dto.response.TopicDigestItemResponse;
import com.caisj.stockdashboard.backend.dto.response.TopicDigestResponse;
import com.caisj.stockdashboard.backend.dto.response.TrumpNewsItemResponse;
import com.caisj.stockdashboard.backend.service.MacroNewsService;
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
                    localizeHeadline(item.title()),
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
        String titleZh = localizeHeadline(entry.title());
        String summaryZh = summarizeTrumpItem(entry.title(), entry.description());
        return new TrumpNewsItemResponse(
            source,
            formatPub(entry.pubDate()),
            cleanText(entry.title()),
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
            cleanText(entry.title()),
            cleanText(entry.description()),
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
        String haystack = (cleanText(entry.title()) + " " + cleanText(entry.description())).toLowerCase(Locale.ROOT);
        List<String> tags = new ArrayList<>();
        if (haystack.contains("tariff")) tags.add("关税");
        if (haystack.contains("trade")) tags.add("贸易");
        if (haystack.contains("china")) tags.add("中国");
        if (haystack.contains("fed")) tags.add("美联储");
        if (haystack.contains("election")) tags.add("选举");
        if (haystack.contains("tax")) tags.add("税收");
        if (haystack.contains("tech")) tags.add("科技");
        return tags;
    }

    private List<String> collectDrivers(List<FeedItem> items, String topic) {
        Set<String> drivers = new LinkedHashSet<>();
        for (FeedItem item : items) {
            String haystack = (item.title() + " " + item.description()).toLowerCase(Locale.ROOT);
            if (haystack.contains("tariff")) drivers.add("关税压力");
            if (haystack.contains("yen")) drivers.add("日元波动");
            if (containsAny(haystack, "japan", "bank of japan", "boj")) drivers.add("日本宏观");
            if (haystack.contains("toyota")) drivers.add("汽车需求");
            if (containsAny(haystack, "chip", "semiconductor", "hbm")) drivers.add("芯片周期");
            if (haystack.contains("ai")) drivers.add("AI 资本开支");
            if (drivers.size() >= 3) break;
        }
        if (drivers.isEmpty()) {
            drivers.add("semiconductor".equals(topic) ? "科技主线" : "宏观主线");
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
        String reason = drivers.isEmpty() ? "宏观消息" : String.join("、", drivers);
        return label + "当前基调为" + tone + "，已汇总 " + count + " 条相关新闻，主线聚焦 " + reason + "。";
    }

    private String localizeHeadline(String title) {
        String clean = cleanText(title);
        if (clean.isBlank()) {
            return "";
        }
        String lower = clean.toLowerCase(Locale.ROOT);

        if (lower.contains("fast retailing") && lower.contains("record high")) {
            return "迅销在上调利润预期后股价飙升至历史新高";
        }
        if (lower.contains("trump administration") && lower.contains("big tech") && lower.contains("fines")) {
            return "欧盟大型科技罚款两年超 70 亿美元，特朗普政府不满升级";
        }
        if (lower.contains("alibaba") && lower.contains("investment") && lower.contains("ai model")) {
            return "阿里巴巴领投 2.9 亿美元，用于建设新型 AI 模型";
        }
        if (lower.contains("openai") && lower.contains("anthropic") && lower.contains("shareholders")) {
            return "OpenAI 在致股东备忘录中点名 Anthropic，强调 AI 竞争升温";
        }

        String localized = clean;
        localized = replaceIgnoreCase(localized, "Fast Retailing", "迅销");
        localized = replaceIgnoreCase(localized, "Uniqlo", "优衣库");
        localized = replaceIgnoreCase(localized, "Trump administration", "特朗普政府");
        localized = replaceIgnoreCase(localized, "European Commission", "欧盟委员会");
        localized = replaceIgnoreCase(localized, "European Union", "欧盟");
        localized = replaceIgnoreCase(localized, "Big Tech", "大型科技公司");
        localized = replaceIgnoreCase(localized, "Alibaba", "阿里巴巴");
        localized = replaceIgnoreCase(localized, "OpenAI", "OpenAI");
        localized = replaceIgnoreCase(localized, "Anthropic", "Anthropic");
        localized = replaceIgnoreCase(localized, "shares soar", "股价大涨");
        localized = replaceIgnoreCase(localized, "record high", "历史新高");
        localized = replaceIgnoreCase(localized, "lifts profit forecast", "上调利润预期");
        localized = replaceIgnoreCase(localized, "investment", "投资");
        localized = replaceIgnoreCase(localized, "memo to shareholders", "致股东备忘录");
        localized = replaceIgnoreCase(localized, "gains momentum", "势头增强");
        localized = replaceIgnoreCase(localized, "fines", "罚款");
        localized = replaceIgnoreCase(localized, "new kind of AI model", "新型 AI 模型");
        return localized;
    }

    private String summarizeTrumpItem(String title, String description) {
        String combined = (cleanText(title) + " " + cleanText(description)).toLowerCase(Locale.ROOT);
        if (combined.contains("tariff")) {
            return "关税相关表态再次升温，市场更关注贸易政策与跨境资产定价影响。";
        }
        if (combined.contains("big tech") && combined.contains("fine")) {
            return "欧盟对大型科技公司的罚款升级，特朗普政府与欧盟监管摩擦继续升温。";
        }
        if (combined.contains("white house")) {
            return "消息聚焦白宫最新政策口径，短线可能影响风险偏好与全球宏观预期。";
        }
        if (combined.contains("trade")) {
            return "内容围绕贸易政策与对外关系，市场通常会同步关注关税、汇率与出口链影响。";
        }
        return summarizeToChinese(title, description, "trump");
    }

    private String summarizeToChinese(String title, String description, String topic) {
        String combined = (cleanText(title) + " " + cleanText(description)).toLowerCase(Locale.ROOT);
        if ("nikkei".equals(topic)) {
            if (combined.contains("fast retailing") && combined.contains("profit forecast")) {
                return "迅销上调利润预期后股价大涨，日本消费与龙头权重板块情绪受提振。";
            }
            if (combined.contains("yen")) {
                return "消息主线与日元走势相关，可能继续影响出口股和日经指数权重股表现。";
            }
            if (containsAny(combined, "japan", "bank of japan", "boj")) {
                return "新闻聚焦日本宏观与政策环境，市场会继续观察日股风险偏好变化。";
            }
        }
        if ("semiconductor".equals(topic)) {
            if (combined.contains("ai model") || combined.contains("ai")) {
                return "新闻围绕 AI 投资与算力扩张，利多半导体链资本开支与需求预期。";
            }
            if (containsAny(combined, "chip", "semiconductor", "tsmc", "hbm", "gpu")) {
                return "内容与芯片周期或先进制程相关，市场关注科技链景气度延续情况。";
            }
        }
        if (combined.contains("openai") && combined.contains("anthropic")) {
            return "OpenAI 与 Anthropic 的竞争被重新强调，AI 模型商业化与融资节奏仍是焦点。";
        }
        if (combined.contains("alibaba") && combined.contains("investment")) {
            return "阿里巴巴参与 AI 模型融资，反映行业仍在加码通用模型与应用落地。";
        }
        if (combined.contains("record high")) {
            return "相关新闻偏利多，核心公司创出新高，板块情绪明显改善。";
        }
        return fallbackChineseSummary(title, description);
    }

    private String fallbackChineseSummary(String title, String description) {
        String cleanTitle = localizeHeadline(title);
        String cleanDesc = cleanText(description);
        if (cleanDesc.isBlank()) {
            return cleanTitle;
        }
        String localizedDesc = replaceIgnoreCase(cleanDesc, "Japan", "日本");
        localizedDesc = replaceIgnoreCase(localizedDesc, "Japanese", "日本");
        localizedDesc = replaceIgnoreCase(localizedDesc, "semiconductor", "半导体");
        localizedDesc = replaceIgnoreCase(localizedDesc, "chip", "芯片");
        localizedDesc = replaceIgnoreCase(localizedDesc, "AI", "AI");
        localizedDesc = replaceIgnoreCase(localizedDesc, "tariff", "关税");
        localizedDesc = replaceIgnoreCase(localizedDesc, "trade", "贸易");
        localizedDesc = replaceIgnoreCase(localizedDesc, "shares", "股价");
        localizedDesc = replaceIgnoreCase(localizedDesc, "profit forecast", "利润预期");
        localizedDesc = shorten(localizedDesc, 60);
        return localizedDesc.isBlank() ? cleanTitle : localizedDesc;
    }

    private String replaceIgnoreCase(String text, String search, String replacement) {
        return text.replaceAll("(?i)" + java.util.regex.Pattern.quote(search), replacement);
    }

    private String shorten(String value, int max) {
        String text = cleanText(value);
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }

    private String cleanText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Jsoup.parse(value).text().replaceAll("\\s+", " ").trim();
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
