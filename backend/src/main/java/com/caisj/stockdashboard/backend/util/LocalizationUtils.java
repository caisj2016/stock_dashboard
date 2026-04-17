package com.caisj.stockdashboard.backend.util;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;

public final class LocalizationUtils {

    private static final Map<String, String> EXACT_TERMS = new LinkedHashMap<>();
    private static final Map<String, String> PHRASE_TERMS = new LinkedHashMap<>();

    static {
        EXACT_TERMS.put("technology", "科技");
        EXACT_TERMS.put("consumer cyclical", "可选消费");
        EXACT_TERMS.put("consumer defensive", "必选消费");
        EXACT_TERMS.put("financial services", "金融服务");
        EXACT_TERMS.put("healthcare", "医疗健康");
        EXACT_TERMS.put("industrials", "工业");
        EXACT_TERMS.put("basic materials", "基础材料");
        EXACT_TERMS.put("communication services", "通信服务");
        EXACT_TERMS.put("energy", "能源");
        EXACT_TERMS.put("utilities", "公用事业");
        EXACT_TERMS.put("real estate", "房地产");

        EXACT_TERMS.put("semiconductors", "半导体");
        EXACT_TERMS.put("semiconductor equipment & materials", "半导体设备与材料");
        EXACT_TERMS.put("consumer electronics", "消费电子");
        EXACT_TERMS.put("auto manufacturers", "汽车制造");
        EXACT_TERMS.put("auto parts", "汽车零部件");
        EXACT_TERMS.put("banks - regional", "区域性银行");
        EXACT_TERMS.put("banks - diversified", "综合性银行");
        EXACT_TERMS.put("insurance - diversified", "综合保险");
        EXACT_TERMS.put("telecom services", "电信服务");
        EXACT_TERMS.put("electronic components", "电子元件");
        EXACT_TERMS.put("specialty industrial machinery", "专用工业机械");
        EXACT_TERMS.put("internet retail", "互联网零售");
        EXACT_TERMS.put("software - infrastructure", "基础软件");
        EXACT_TERMS.put("software - application", "应用软件");

        EXACT_TERMS.put("japan", "日本");
        EXACT_TERMS.put("united states", "美国");
        EXACT_TERMS.put("tokyo", "东京");
        EXACT_TERMS.put("tse", "东京证券交易所");
        EXACT_TERMS.put("tokyo stock exchange", "东京证券交易所");
        EXACT_TERMS.put("equity", "股票");
    }

    static {
        PHRASE_TERMS.put("fast retailing", "迅销");
        PHRASE_TERMS.put("uniqlo", "优衣库");
        PHRASE_TERMS.put("trump administration", "特朗普政府");
        PHRASE_TERMS.put("white house", "白宫");
        PHRASE_TERMS.put("president trump", "特朗普总统");
        PHRASE_TERMS.put("donald trump", "唐纳德·特朗普");
        PHRASE_TERMS.put("european commission", "欧盟委员会");
        PHRASE_TERMS.put("european union", "欧盟");
        PHRASE_TERMS.put("big tech", "大型科技公司");
        PHRASE_TERMS.put("record high", "历史新高");
        PHRASE_TERMS.put("shares soar", "股价大涨");
        PHRASE_TERMS.put("lifts profit forecast", "上调盈利预期");
        PHRASE_TERMS.put("profit forecast", "盈利预期");
        PHRASE_TERMS.put("memo to shareholders", "致股东备忘录");
        PHRASE_TERMS.put("gains momentum", "动能增强");
        PHRASE_TERMS.put("new kind of ai model", "新一代 AI 模型");
        PHRASE_TERMS.put("semiconductor", "半导体");
        PHRASE_TERMS.put("chip", "芯片");
        PHRASE_TERMS.put("chips", "芯片");
        PHRASE_TERMS.put("tariff", "关税");
        PHRASE_TERMS.put("trade war", "贸易战");
        PHRASE_TERMS.put("trade", "贸易");
        PHRASE_TERMS.put("shares", "股价");
        PHRASE_TERMS.put("investment", "投资");
        PHRASE_TERMS.put("fines", "罚款");
        PHRASE_TERMS.put("fine", "罚款");
    }

    private LocalizationUtils() {
    }

    public static String cleanText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return Jsoup.parse(value).text().replaceAll("\\s+", " ").trim();
    }

    public static String localizeDisplayText(String value) {
        String clean = cleanText(value);
        if (clean.isBlank()) {
            return "--";
        }
        return localizeFinancialText(clean);
    }

    public static String localizeFinancialText(String value) {
        String clean = cleanText(value);
        if (clean.isBlank()) {
            return "";
        }

        String exact = EXACT_TERMS.get(clean.toLowerCase(Locale.ROOT));
        if (exact != null) {
            return exact;
        }

        String localized = clean;
        for (Map.Entry<String, String> entry : PHRASE_TERMS.entrySet()) {
            localized = replaceIgnoreCase(localized, entry.getKey(), entry.getValue());
        }
        return localized;
    }

    public static String localizeHeadline(String value) {
        String localized = localizeFinancialText(value);
        String lower = localized.toLowerCase(Locale.ROOT);

        if (lower.contains("迅销") && lower.contains("历史新高")) {
            return "迅销上调盈利预期，股价走强并刷新历史新高";
        }
        if (lower.contains("大型科技公司") && lower.contains("罚款")) {
            return "欧美对大型科技公司罚款加码，科技监管压力再升温";
        }
        if (lower.contains("阿里巴巴") && lower.contains("ai")) {
            return "阿里继续加码 AI 投资，推进新模型和相关应用";
        }
        if (lower.contains("openai") && lower.contains("anthropic")) {
            return "OpenAI 与 Anthropic 融资动态再受关注，AI 产业竞争持续升温";
        }
        return localized;
    }

    private static String replaceIgnoreCase(String text, String search, String replacement) {
        return Pattern.compile(Pattern.quote(search), Pattern.CASE_INSENSITIVE)
            .matcher(text)
            .replaceAll(replacement);
    }
}
