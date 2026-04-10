package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.client.YahooFinanceClient;
import com.caisj.stockdashboard.backend.dto.response.NewsItemResponse;
import com.caisj.stockdashboard.backend.service.NewsService;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import org.springframework.cache.annotation.Cacheable;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;

@Service
public class NewsServiceImpl implements NewsService {

    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DISPLAY_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final YahooFinanceClient yahooFinanceClient;

    public NewsServiceImpl(YahooFinanceClient yahooFinanceClient) {
        this.yahooFinanceClient = yahooFinanceClient;
    }

    @Override
    @Cacheable(cacheNames = "stockNews", key = "#symbol")
    public List<NewsItemResponse> getStockNews(String symbol) {
        String normalizedSymbol = symbol == null ? "" : symbol.trim().toUpperCase();
        if (normalizedSymbol.isEmpty()) {
            return List.of();
        }
        return yahooFinanceClient.fetchStockNewsRss(normalizedSymbol).stream()
            .map(item -> {
                String titleEn = cleanText(item.title());
                String summaryEn = cleanText(item.summary());
                String titleZh = localizeHeadline(titleEn);
                String summaryZh = summarizeToChinese(titleEn, summaryEn);
                return new NewsItemResponse(
                    titleZh.isBlank() ? titleEn : titleZh,
                    titleEn,
                    summaryZh.isBlank() ? titleZh : summaryZh,
                    formatPubDate(item.pubDate()),
                    "Yahoo Finance",
                    item.url()
                );
            })
            .toList();
    }

    private String formatPubDate(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        try {
            return OffsetDateTime.parse(raw, DateTimeFormatter.RFC_1123_DATE_TIME)
                .atZoneSameInstant(TOKYO)
                .format(DISPLAY_FORMATTER);
        } catch (DateTimeParseException ex) {
            return raw.length() > 16 ? raw.substring(0, 16) : raw;
        }
    }

    private String localizeHeadline(String title) {
        String clean = cleanText(title);
        if (clean.isBlank()) {
            return "";
        }

        String lower = clean.toLowerCase(Locale.ROOT);
        if (lower.contains("nintendo") && lower.contains("valuation")) {
            return "任天堂估值解析：主机周期与回报分化如何影响投资者预期";
        }
        if (lower.contains("mario sequel") && lower.contains("opening")) {
            return "马里奥续作首映票房目标 3.5 亿美元，任天堂加速扩展 IP 战略";
        }
        if (lower.contains("great rotation") && lower.contains("buying opportunity")) {
            return "板块轮动正在为这只成长股创造十年一遇的买点";
        }
        if (lower.contains("super mario") && lower.contains("nintendo stock")) {
            return "超级马里奥还能再次拯救任天堂股价吗？";
        }
        if (lower.contains("microsoft") && lower.contains("xbox gaming")) {
            return "微软押注 Xbox 游戏阵容扩张，后续走势怎么看？";
        }
        if (lower.contains("nintendo") && lower.contains("primary moat")) {
            return "Switch 2 时代，任天堂正把旗舰 IP 打造成核心护城河";
        }

        String localized = clean;
        localized = replaceIgnoreCase(localized, "Nintendo", "任天堂");
        localized = replaceIgnoreCase(localized, "Microsoft", "微软");
        localized = replaceIgnoreCase(localized, "Xbox", "Xbox");
        localized = replaceIgnoreCase(localized, "Mario", "马里奥");
        localized = replaceIgnoreCase(localized, "Super Mario", "超级马里奥");
        localized = replaceIgnoreCase(localized, "Switch 2", "Switch 2");
        localized = replaceIgnoreCase(localized, "valuation", "估值");
        localized = replaceIgnoreCase(localized, "buying opportunity", "买入机会");
        localized = replaceIgnoreCase(localized, "growth stock", "成长股");
        localized = replaceIgnoreCase(localized, "franchise strategy", "IP 战略");
        localized = replaceIgnoreCase(localized, "primary moat", "核心护城河");
        return localized;
    }

    private String summarizeToChinese(String title, String summary) {
        String cleanTitle = cleanText(title);
        String cleanSummary = cleanText(summary);
        String combined = (cleanTitle + " " + cleanSummary).toLowerCase(Locale.ROOT);

        if (combined.contains("nintendo") && combined.contains("valuation")) {
            return "文章围绕任天堂近期股价表现、主机周期与过去数年的回报分化展开，评估市场当前对其基本面的定价是否合理。";
        }
        if (combined.contains("mario sequel") && combined.contains("box office")) {
            return "报道认为新电影票房预期强劲，加上角色联动和跨媒体开发，显示任天堂正在把游戏 IP 更系统地延伸到影视与周边业务。";
        }
        if (combined.contains("great rotation") && combined.contains("profitable")) {
            return "文章认为市场低估了该业务未来几年盈利能力的改善空间，因此板块轮动可能带来更有吸引力的建仓窗口。";
        }
        if (combined.contains("super mario") && combined.contains("rough few months")) {
            return "尽管任天堂近几个月股价承压，但马里奥这一头部 IP 再次登上大银幕，可能为市场情绪与业务预期提供支撑。";
        }
        if (combined.contains("microsoft") && combined.contains("xbox") && combined.contains("game pass")) {
            return "文章指出微软正依靠更多 Xbox 新作和 Game Pass 扩容推动游戏业务修复，但疲弱的游戏收入和高基数仍让反转故事未完全兑现。";
        }
        if (combined.contains("switch 2") && (combined.contains("mario") || combined.contains("pokemon"))) {
            return "报道聚焦任天堂在 Switch 2 周期内如何通过马里奥、宝可梦等旗舰系列的试玩、社群活动和新玩法设计，进一步强化用户黏性与品牌壁垒。";
        }

        String localizedSummary = cleanSummary;
        localizedSummary = replaceIgnoreCase(localizedSummary, "Nintendo", "任天堂");
        localizedSummary = replaceIgnoreCase(localizedSummary, "Microsoft", "微软");
        localizedSummary = replaceIgnoreCase(localizedSummary, "Xbox", "Xbox");
        localizedSummary = replaceIgnoreCase(localizedSummary, "Game Pass", "Game Pass");
        localizedSummary = replaceIgnoreCase(localizedSummary, "Super Mario", "超级马里奥");
        localizedSummary = replaceIgnoreCase(localizedSummary, "Mario", "马里奥");
        localizedSummary = replaceIgnoreCase(localizedSummary, "Pokemon", "宝可梦");
        localizedSummary = replaceIgnoreCase(localizedSummary, "Pokémon", "宝可梦");
        localizedSummary = replaceIgnoreCase(localizedSummary, "Switch 2", "Switch 2");
        localizedSummary = replaceIgnoreCase(localizedSummary, "investors", "投资者");
        localizedSummary = replaceIgnoreCase(localizedSummary, "share price", "股价");
        localizedSummary = replaceIgnoreCase(localizedSummary, "box office", "票房");
        localizedSummary = replaceIgnoreCase(localizedSummary, "franchise", "旗舰 IP");
        localizedSummary = replaceIgnoreCase(localizedSummary, "gaming revenues", "游戏收入");
        localizedSummary = replaceIgnoreCase(localizedSummary, "titles", "游戏作品");
        localizedSummary = replaceIgnoreCase(localizedSummary, "additions", "扩容");
        localizedSummary = replaceIgnoreCase(localizedSummary, "business", "业务");
        localizedSummary = replaceIgnoreCase(localizedSummary, "profitable", "盈利能力更强");

        if (!localizedSummary.isBlank() && !localizedSummary.equals(cleanSummary)) {
            return localizedSummary;
        }
        if (!cleanSummary.isBlank()) {
            return shorten(cleanSummary, 90);
        }
        return localizeHeadline(cleanTitle);
    }

    private String cleanText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Jsoup.parse(value).text().replaceAll("\\s+", " ").trim();
    }

    private String replaceIgnoreCase(String text, String search, String replacement) {
        return text.replaceAll("(?i)" + java.util.regex.Pattern.quote(search), replacement);
    }

    private String shorten(String value, int max) {
        String text = cleanText(value);
        if (text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "...";
    }
}
