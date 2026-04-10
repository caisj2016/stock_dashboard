package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.domain.model.ScreenerCandidate;
import com.caisj.stockdashboard.backend.domain.model.ScreenerUniverseDefinition;
import com.caisj.stockdashboard.backend.service.ScreenerUniverseService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class ScreenerUniverseServiceImpl implements ScreenerUniverseService {

    private static final List<ScreenerCandidate> CORE_45 = List.of(
        new ScreenerCandidate("1332.T", "ニッスイ"),
        new ScreenerCandidate("1605.T", "ＩＮＰＥＸ"),
        new ScreenerCandidate("1802.T", "大林組"),
        new ScreenerCandidate("1925.T", "大和ハウス工業"),
        new ScreenerCandidate("2914.T", "日本たばこ産業"),
        new ScreenerCandidate("3382.T", "セブン＆アイ・ホールディングス"),
        new ScreenerCandidate("4063.T", "信越化学工業"),
        new ScreenerCandidate("4502.T", "武田薬品工業"),
        new ScreenerCandidate("4568.T", "第一三共"),
        new ScreenerCandidate("6098.T", "リクルートホールディングス"),
        new ScreenerCandidate("6501.T", "日立製作所"),
        new ScreenerCandidate("6503.T", "三菱電機"),
        new ScreenerCandidate("6752.T", "パナソニック ホールディングス"),
        new ScreenerCandidate("6758.T", "ソニーグループ"),
        new ScreenerCandidate("6762.T", "ＴＤＫ"),
        new ScreenerCandidate("6857.T", "アドバンテスト"),
        new ScreenerCandidate("6902.T", "デンソー"),
        new ScreenerCandidate("6954.T", "ファナック"),
        new ScreenerCandidate("6981.T", "村田製作所"),
        new ScreenerCandidate("7201.T", "日産自動車"),
        new ScreenerCandidate("7203.T", "トヨタ自動車"),
        new ScreenerCandidate("7267.T", "本田技研工業"),
        new ScreenerCandidate("7733.T", "オリンパス"),
        new ScreenerCandidate("7741.T", "ＨＯＹＡ"),
        new ScreenerCandidate("7751.T", "キヤノン"),
        new ScreenerCandidate("7974.T", "任天堂"),
        new ScreenerCandidate("8001.T", "伊藤忠商事"),
        new ScreenerCandidate("8002.T", "丸紅"),
        new ScreenerCandidate("8031.T", "三井物産"),
        new ScreenerCandidate("8035.T", "東京エレクトロン"),
        new ScreenerCandidate("8058.T", "三菱商事"),
        new ScreenerCandidate("8306.T", "三菱ＵＦＪフィナンシャル・グループ"),
        new ScreenerCandidate("8316.T", "三井住友フィナンシャルグループ"),
        new ScreenerCandidate("8411.T", "みずほフィナンシャルグループ"),
        new ScreenerCandidate("8591.T", "オリックス"),
        new ScreenerCandidate("8766.T", "東京海上ホールディングス"),
        new ScreenerCandidate("9020.T", "東日本旅客鉄道"),
        new ScreenerCandidate("9432.T", "日本電信電話"),
        new ScreenerCandidate("9433.T", "ＫＤＤＩ"),
        new ScreenerCandidate("9434.T", "ソフトバンク"),
        new ScreenerCandidate("9501.T", "東京電力ホールディングス"),
        new ScreenerCandidate("9503.T", "関西電力"),
        new ScreenerCandidate("9735.T", "セコム"),
        new ScreenerCandidate("9983.T", "ファーストリテイリング"),
        new ScreenerCandidate("9984.T", "ソフトバンクグループ")
    );

    private static final Set<String> TOPIX_CORE_30_CODES = Set.of(
        "2914.T", "4063.T", "4502.T", "4568.T", "6501.T", "6758.T", "6857.T", "6902.T", "6954.T", "6981.T",
        "7203.T", "7741.T", "7974.T", "8001.T", "8031.T", "8035.T", "8058.T", "8306.T", "8316.T", "8411.T",
        "8591.T", "8766.T", "9020.T", "9432.T", "9433.T", "9434.T", "9503.T", "9735.T", "9983.T", "9984.T"
    );

    private static final Map<String, ScreenerUniverseDefinition> UNIVERSES = Map.of(
        "core45", new ScreenerUniverseDefinition("core45", "核心45", "当前维护最快的核心股票池", CORE_45),
        "topixcore", new ScreenerUniverseDefinition(
            "topixcore",
            "TOPIX Core 30",
            "更偏大盘权重股的 30 只核心池",
            CORE_45.stream().filter(item -> TOPIX_CORE_30_CODES.contains(item.code())).toList()
        ),
        "nikkei225", new ScreenerUniverseDefinition("nikkei225", "Nikkei 225", "当前阶段先复用已维护的核心池，后续再扩成全量样本", CORE_45)
    );

    @Override
    public ScreenerUniverseDefinition getUniverse(String universeKey) {
        String normalized = universeKey == null ? "core45" : universeKey.trim().toLowerCase();
        return UNIVERSES.getOrDefault(normalized, UNIVERSES.get("core45"));
    }
}
