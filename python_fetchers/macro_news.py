import time


def fetch_trump_news_items(
    *,
    cache,
    cache_ttl,
    parse_social_rss,
    parse_rss,
    translate_to_zh,
    decorate_market_item,
    trump_rss_feeds,
    trump_keyword_pattern,
    truth_rss_url,
    x_rss_url,
):
    now = time.time()
    if "trump" in cache and now - cache["trump"]["ts"] < cache_ttl:
        return cache["trump"]["data"]

    all_items = []
    all_items.extend(
        parse_social_rss(
            truth_rss_url,
            "TRUTH",
            limit=6,
            fallback_url="https://truthsocialapp.com/@realDonaldTrump",
        )
    )
    all_items.extend(
        parse_social_rss(
            x_rss_url,
            "X",
            limit=6,
            fallback_url="https://x.com/realDonaldTrump",
        )
    )
    for feed in trump_rss_feeds:
        all_items.extend(
            parse_rss(
                feed,
                match_fn=lambda title, desc: trump_keyword_pattern.search(title)
                or trump_keyword_pattern.search(desc[:200]),
            )
        )

    seen = set()
    unique = []
    for item in all_items:
        key = (item.get("source", "") + "|" + item.get("title", "")[:60]).lower()
        if key in seen:
            continue
        seen.add(key)
        unique.append(item)

    unique.sort(key=lambda item: item.get("sort_ts", 0), reverse=True)
    top = unique[:12]

    for item in top:
        if not item.get("title_zh"):
            item["title_zh"] = translate_to_zh(item.get("title", ""))
        if item.get("summary") and not item.get("summary_zh"):
            item["summary_zh"] = translate_to_zh(item["summary"])
        decorate_market_item(item)

    relevant = [item for item in top if item.get("is_market_relevant")]
    relevant.sort(key=lambda item: (item.get("market_score", 0), item.get("sort_ts", 0)), reverse=True)
    others = [item for item in top if not item.get("is_market_relevant")]
    others.sort(key=lambda item: item.get("sort_ts", 0), reverse=True)
    top = (relevant[:8] + others[:4])[:12]

    cache["trump"] = {"ts": now, "data": top}
    return top
