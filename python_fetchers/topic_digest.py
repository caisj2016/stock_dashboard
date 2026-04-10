from datetime import timedelta


def normalize_topic_feed_item(item, *, rss_source, pub_ts, fmt_pubdate):
    normalized = dict(item)
    normalized["provider"] = item.get("provider") or item.get("source") or rss_source(item.get("url", ""))
    normalized["summary"] = item.get("summary") or item.get("summary_zh") or ""
    normalized["title"] = item.get("title") or item.get("title_zh") or ""
    normalized["pub_raw"] = item.get("pub_raw") or item.get("pub") or ""
    normalized["sort_ts"] = item.get("sort_ts") or pub_ts(normalized["pub_raw"])
    normalized["pub"] = item.get("pub") or (fmt_pubdate(normalized["pub_raw"]) if normalized["pub_raw"] else "")
    return normalized


def topic_recall_score(item, topic_cfg, *, contains_term):
    title = (item.get("title") or "").lower()
    body = " ".join([item.get("title", ""), item.get("summary", "")]).lower()
    score = 0
    core_hits = 0
    anchor_hits = 0
    for keyword in topic_cfg.get("core_topic_terms", []):
        if contains_term(title, keyword):
            core_hits += 2
        elif contains_term(body, keyword):
            core_hits += 1
    for keyword in topic_cfg.get("anchor_terms", []):
        if contains_term(title, keyword):
            anchor_hits += 2
        elif contains_term(body, keyword):
            anchor_hits += 1
    for keyword in topic_cfg.get("topic_terms", []):
        if contains_term(title, keyword):
            score += 3
        elif contains_term(body, keyword):
            score += 1
    for keywords in topic_cfg.get("drivers", {}).values():
        if any(contains_term(body, keyword) for keyword in keywords):
            score += 4
    return score, core_hits, anchor_hits


def fetch_topic_candidate_items(
    topic_key,
    topic_cfg,
    *,
    parse_google_news_rss,
    parse_rss,
    parse_html_topic_page,
    normalize_topic_feed_item,
    topic_recall_score,
    topic_rss_feeds,
    topic_html_feeds,
    limit=24,
):
    candidates = []
    queries = topic_cfg.get("queries") or [topic_cfg.get("query", "")]
    google_cfg = topic_cfg.get("google_news", {})
    for query in queries:
        google_items = parse_google_news_rss(
            query,
            limit=max(8, limit // max(1, len(queries))),
            hl=google_cfg.get("hl", "en-US"),
            gl=google_cfg.get("gl", "US"),
            ceid=google_cfg.get("ceid", "US:en"),
        )
        candidates.extend(normalize_topic_feed_item(item) for item in google_items)

    for feed in topic_rss_feeds.get(topic_key, []):
        for item in parse_rss(feed):
            normalized = normalize_topic_feed_item(item)
            recall_score, core_hits, anchor_hits = topic_recall_score(normalized, topic_cfg)
            if (core_hits > 0 and anchor_hits > 0) or anchor_hits > 0 or recall_score >= 9:
                candidates.append(normalized)

    for feed in topic_html_feeds.get(topic_key, []):
        for item in parse_html_topic_page(feed["url"], feed["name"], limit=limit):
            normalized = normalize_topic_feed_item(item)
            recall_score, core_hits, anchor_hits = topic_recall_score(normalized, topic_cfg)
            if core_hits > 0 or anchor_hits > 0 or recall_score >= 6:
                candidates.append(normalized)

    return candidates


def score_topic_item(item, topic_cfg, *, contains_term, pub_datetime_jst, now_jst):
    title = item.get("title", "")
    summary = item.get("summary", "")
    title_text = title.lower()
    body_text = " ".join([title, summary]).lower()
    score = 0.0
    hits = []
    topic_hits = 0
    core_topic_hits = 0
    anchor_hits = 0

    for keyword in topic_cfg.get("core_topic_terms", []):
        if contains_term(title_text, keyword):
            core_topic_hits += 2
        elif contains_term(body_text, keyword):
            core_topic_hits += 1
    for keyword in topic_cfg.get("anchor_terms", []):
        if contains_term(title_text, keyword):
            anchor_hits += 2
        elif contains_term(body_text, keyword):
            anchor_hits += 1
    for label, keywords in topic_cfg.get("drivers", {}).items():
        title_match = sum(1 for keyword in keywords if contains_term(title_text, keyword))
        body_match = sum(1 for keyword in keywords if contains_term(body_text, keyword))
        if body_match:
            score += min(12, title_match * 4 + body_match * 2.5)
            hits.append(label)
    for keyword in topic_cfg.get("topic_terms", []):
        if contains_term(title_text, keyword):
            topic_hits += 2
        elif contains_term(body_text, keyword):
            topic_hits += 1
    score += min(10, topic_hits * 1.6)

    provider = (item.get("provider") or "").lower()
    provider_weights = {
        "reuters": 5,
        "bloomberg": 5,
        "nikkei asia": 4,
        "cnbc": 3,
        "financial times": 3,
        "wsj": 3,
    }
    score += provider_weights.get(provider, 1 if provider else 0)

    if any(token in body_text for token in ("update", "outlook", "forecast", "guidance", "exclusive", "analysis")):
        score += 2.5

    pub_dt = pub_datetime_jst(item.get("pub_raw"))
    if pub_dt:
        age_hours = max(0.0, (now_jst - pub_dt).total_seconds() / 3600.0)
        if age_hours <= 12:
            score += 8
        elif age_hours <= 24:
            score += 6
        elif age_hours <= 72:
            score += 4
        elif age_hours <= 7 * 24:
            score += 2
        else:
            score += 0.5

    item["driver_hits"] = hits
    item["digest_score"] = score
    item["topic_hits"] = topic_hits
    item["core_topic_hits"] = core_topic_hits
    item["anchor_hits"] = anchor_hits
    return item


def topic_item_similarity(left, right):
    left_tokens = left.get("title_tokens") or set()
    right_tokens = right.get("title_tokens") or set()
    token_union = left_tokens | right_tokens
    token_sim = (len(left_tokens & right_tokens) / len(token_union)) if token_union else 0.0
    left_drivers = set(left.get("driver_hits") or [])
    right_drivers = set(right.get("driver_hits") or [])
    driver_union = left_drivers | right_drivers
    driver_sim = (len(left_drivers & right_drivers) / len(driver_union)) if driver_union else 0.0
    provider_sim = 0.15 if (left.get("provider") or "").lower() == (right.get("provider") or "").lower() else 0.0
    return min(1.0, token_sim * 0.65 + driver_sim * 0.35 + provider_sim)


def select_diverse_topic_items(items, *, similarity_fn, limit=3):
    remaining = sorted(items, key=lambda item: (item.get("digest_score", 0), item.get("sort_ts", 0)), reverse=True)
    selected = []
    while remaining and len(selected) < limit:
        best_idx = 0
        best_score = None
        for idx, item in enumerate(remaining):
            max_sim = max((similarity_fn(item, picked) for picked in selected), default=0.0)
            mmr_score = item.get("digest_score", 0) - max_sim * 6
            if best_score is None or mmr_score > best_score:
                best_score = mmr_score
                best_idx = idx
        selected.append(remaining.pop(best_idx))
    return selected


def ensure_fresh_digest_items(selected_items, fresh_items, *, select_diverse_items, limit=3):
    if not fresh_items or not selected_items:
        return selected_items
    if any(item in fresh_items for item in selected_items):
        return selected_items
    fresh_pick = select_diverse_items(fresh_items, limit=1)
    if not fresh_pick:
        return selected_items
    fresh_item = fresh_pick[0]
    if len(selected_items) < limit:
        return selected_items + [fresh_item]
    replaced = list(selected_items)
    replaced[-1] = fresh_item
    return replaced


def translate_digest_items(items, *, translate_to_zh):
    for item in items:
        item["title_zh"] = translate_to_zh(item.get("title", ""))
        brief_src = item.get("summary") or item.get("title", "")
        item["brief"] = translate_to_zh(brief_src[:110]) if brief_src else ""
    return items


def sanitize_digest_items(items):
    sanitized = []
    for item in items:
        clean = dict(item)
        clean.pop("title_tokens", None)
        sanitized.append(clean)
    return sanitized


def is_strong_topic_item(item):
    return (item.get("core_topic_hits") or 0) > 0 and (item.get("anchor_hits") or 0) > 0


def is_medium_topic_item(item):
    anchor_hits = item.get("anchor_hits") or 0
    topic_hits = item.get("topic_hits") or 0
    driver_hits = item.get("driver_hits") or []
    return anchor_hits > 0 or (topic_hits >= 2 and len(driver_hits) >= 1)


def is_macro_fallback_item(item):
    return (item.get("topic_hits") or 0) > 0 or (item.get("core_topic_hits") or 0) > 0 or (item.get("anchor_hits") or 0) > 0


def is_topic_eligible(item):
    return is_strong_topic_item(item) or is_medium_topic_item(item) or is_macro_fallback_item(item)


def detect_digest_tone(items, topic_cfg, *, contains_term, quote_pct_fetcher):
    score = 0
    texts = [" ".join([it.get("title", ""), it.get("summary", "")]).lower() for it in items]
    for text in texts:
        score += sum(1 for word in topic_cfg.get("positive", []) if contains_term(text, word))
        score -= sum(1 for word in topic_cfg.get("negative", []) if contains_term(text, word))

    quote_symbol = topic_cfg.get("quote_symbol")
    if quote_symbol:
        try:
            pct = quote_pct_fetcher(quote_symbol)
            if pct is not None:
                if pct >= 0.8:
                    score += 3
                elif pct >= 0.2:
                    score += 1
                elif pct <= -0.8:
                    score -= 3
                elif pct <= -0.2:
                    score -= 1
        except Exception:
            pass

    if score >= 3:
        return "偏多", "up"
    if score <= -3:
        return "偏空", "down"
    return "震荡", "neutral"


def build_digest_summary_text(topic, tone, drivers, *, lead_map, impact_map, default_reason="市场主线"):
    reason = "、".join(drivers[:3]) if drivers else default_reason
    lead = lead_map.get(topic, {}).get(tone, "")
    impact = "；".join(
        impact_map.get(topic, {}).get(driver, "")
        for driver in drivers[:2]
        if impact_map.get(topic, {}).get(driver)
    )
    if impact:
        return f"{lead} 当前主导因素集中在{reason}；对盘面的直接影响主要体现在{impact}。"
    return f"{lead} 当前主导因素集中在{reason}。"


def build_default_digest_summary(topic, tone, drivers):
    lead_map = {
        "nikkei": {
            "偏多": "今天日经更偏多，市场主线在顺风因素和核心权重股之间扩散。",
            "偏空": "今天日经更偏空，盘面更像被汇率、避险和风险偏好回落拖累。",
            "震荡": "今天日经以拉锯为主，暂时没有单一主线完全占优。",
        },
        "semiconductor": {
            "偏多": "今天半导体板块偏强，AI 链条和龙头公司消息继续提供支撑。",
            "偏空": "今天半导体板块偏弱，需求、估值或情绪端都有一定压制。",
            "震荡": "今天半导体板块偏震荡，催化存在但分歧也比较明显。",
        },
    }
    impact_map = {
        "nikkei": {
            "日元汇率": "出口权重股的盈利预期与估值弹性",
            "日本利率": "金融股表现和整体风险偏好",
            "全球风险偏好": "外资回流与指数权重股的同步性",
            "日本核心权重": "指数方向和市场主线集中度",
            "能源与原材料": "输入型通胀与制造业利润压力",
            "政策与监管": "市场对后续宽松、财政或产业支持的预期",
            "海外宏观事件": "避险情绪和跨市场联动",
        },
        "semiconductor": {
            "AI基础设施": "算力、GPU 与服务器链条的景气预期",
            "全球资本开支": "设备、材料与代工环节的订单能见度",
            "龙头公司业绩": "估值锚和板块情绪",
            "HBM与先进封装": "高端存储和封装链条的盈利弹性",
            "大模型与AI应用": "新增需求能否继续向上游传导",
            "日本半导体政策": "本土设备、材料与制造环节的受益预期",
            "供应链扰动": "交付节奏和短期风险溢价",
        },
    }
    return build_digest_summary_text(
        topic,
        tone,
        drivers,
        lead_map=lead_map,
        impact_map=impact_map,
    )


def build_topic_digest(
    topic_key,
    *,
    force,
    cache,
    cache_ttl,
    topic_config,
    fetch_topic_candidate_items,
    score_topic_item,
    is_today_jst,
    pub_datetime_jst,
    now_jst,
    recent_hours,
    max_age_days,
    older_max_days,
    select_diverse_topic_items,
    ensure_fresh_digest_items,
    is_strong_topic_item,
    is_medium_topic_item,
    is_macro_fallback_item,
    is_topic_eligible,
    translate_digest_items,
    sanitize_digest_items,
    detect_digest_tone,
    build_digest_summary,
    time_module,
    datetime_module,
    jst,
):
    if topic_key not in topic_config:
        return {"error": "unknown topic"}

    cache_key = f"digest_{topic_key}"
    now = time_module.time()
    if not force and cache_key in cache and now - cache[cache_key]["ts"] < cache_ttl:
        return cache[cache_key]["data"]

    cfg = topic_config[topic_key]
    items = fetch_topic_candidate_items(topic_key, cfg, limit=18)
    seen = set()
    deduped = []
    for item in items:
        key = " ".join((item.get("title", "")[:100]).lower().split())
        if key in seen:
            continue
        seen.add(key)
        deduped.append(score_topic_item(item, cfg))

    current_jst = now_jst()
    same_day_items = [item for item in deduped if is_today_jst(item.get("pub_raw"))]
    recent_items = []
    stale_items = []
    older_items = []
    for item in deduped:
        if item in same_day_items:
            continue
        pub_dt = pub_datetime_jst(item.get("pub_raw"))
        if not pub_dt:
            stale_items.append(item)
            continue
        age = current_jst - pub_dt
        if age <= timedelta(hours=recent_hours):
            recent_items.append(item)
        elif age <= timedelta(days=max_age_days):
            stale_items.append(item)
        elif age <= timedelta(days=older_max_days):
            older_items.append(item)

    same_day_items.sort(key=lambda x: (x.get("digest_score", 0), x.get("sort_ts", 0)), reverse=True)
    recent_items.sort(key=lambda x: (x.get("digest_score", 0), x.get("sort_ts", 0)), reverse=True)
    stale_items.sort(key=lambda x: (x.get("digest_score", 0), x.get("sort_ts", 0)), reverse=True)
    older_items.sort(key=lambda x: (x.get("digest_score", 0), x.get("sort_ts", 0)), reverse=True)

    same_day_strong = [item for item in same_day_items if is_strong_topic_item(item)]
    recent_strong = [item for item in recent_items if is_strong_topic_item(item)]
    stale_strong = [item for item in stale_items if is_strong_topic_item(item)]
    older_strong = [item for item in older_items if is_strong_topic_item(item)]
    same_day_medium = [item for item in same_day_items if item not in same_day_strong and is_medium_topic_item(item)]
    recent_medium = [item for item in recent_items if item not in recent_strong and is_medium_topic_item(item)]
    stale_medium = [item for item in stale_items if item not in stale_strong and is_medium_topic_item(item)]
    older_medium = [item for item in older_items if item not in older_strong and is_medium_topic_item(item)]

    selected_items = select_diverse_topic_items(same_day_strong, limit=3)
    if len(selected_items) < 3:
        selected_items.extend(select_diverse_topic_items(same_day_medium, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        selected_items.extend(select_diverse_topic_items(recent_strong, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        selected_items.extend(select_diverse_topic_items(recent_medium, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        selected_items.extend(select_diverse_topic_items(stale_strong, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        selected_items.extend(select_diverse_topic_items(stale_medium, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        selected_items.extend(select_diverse_topic_items(older_strong, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        selected_items.extend(select_diverse_topic_items(older_medium, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        macro_fallback = [
            item for item in (same_day_items + recent_items + stale_items + older_items)
            if item not in selected_items and is_macro_fallback_item(item)
        ]
        selected_items.extend(select_diverse_topic_items(macro_fallback, limit=3 - len(selected_items)))

    selected_items = ensure_fresh_digest_items(
        selected_items,
        same_day_items + recent_items,
        limit=3,
    )
    selected_items = [item for item in selected_items if is_topic_eligible(item)]

    top_items = sanitize_digest_items(translate_digest_items(selected_items))

    if same_day_strong:
        driver_source = same_day_strong
    elif recent_strong:
        driver_source = recent_strong
    elif stale_strong:
        driver_source = stale_strong
    elif older_strong:
        driver_source = older_strong
    elif same_day_medium:
        driver_source = same_day_medium
    elif recent_medium:
        driver_source = recent_medium
    elif stale_medium:
        driver_source = stale_medium
    elif older_medium:
        driver_source = older_medium
    elif same_day_items:
        driver_source = same_day_items
    elif recent_items:
        driver_source = recent_items
    elif stale_items:
        driver_source = stale_items
    else:
        driver_source = older_items

    driver_counts = {}
    for item in driver_source[:6]:
        for label in item.get("driver_hits", []):
            driver_counts[label] = driver_counts.get(label, 0) + 1
    drivers = [label for label, _ in sorted(driver_counts.items(), key=lambda kv: (-kv[1], kv[0]))[:3]]
    if not drivers and top_items:
        drivers = [cfg.get("fallback_driver", "Latest market development")]

    tone, tone_class = detect_digest_tone(top_items, cfg)
    result = {
        "topic": topic_key,
        "title": cfg["title"],
        "tone": tone,
        "tone_class": tone_class,
        "summary": build_digest_summary(topic_key, tone, drivers),
        "drivers": drivers,
        "items": top_items,
        "updated": datetime_module.now(jst).strftime("%H:%M"),
        "date_scope": "today" if same_day_items else ("recent_72h" if recent_items else ("latest_available" if (stale_items or older_items) else "stale_or_empty")),
        "available_recent_items": len(same_day_items) + len(recent_items),
        "available_stale_items": len(stale_items) + len(older_items),
        "debug_counts": {
            "raw_candidates": len(items),
            "deduped": len(deduped),
            "same_day": len(same_day_items),
            "recent": len(recent_items),
            "stale": len(stale_items),
            "older": len(older_items),
            "same_day_strong": len(same_day_strong),
            "recent_strong": len(recent_strong),
            "stale_strong": len(stale_strong),
            "older_strong": len(older_strong),
            "same_day_medium": len(same_day_medium),
            "recent_medium": len(recent_medium),
            "stale_medium": len(stale_medium),
            "older_medium": len(older_medium),
            "selected": len(top_items),
        },
    }
    cache[cache_key] = {"ts": now, "data": result}
    return result
