import re
import xml.etree.ElementTree as ET
from html import unescape
from urllib.parse import quote_plus

import requests


def rss_source(url):
    host = url.split("/")[2]
    for part in ["reuters", "apnews", "thehill", "bbc"]:
        if part in host:
            return part.upper()
    return host.replace("www.", "").replace("feeds.", "")


def parse_rss_feed(url, *, headers, pub_ts, fmt_pubdate, match_fn=None):
    try:
        response = requests.get(url, timeout=10, headers=headers)
        root = ET.fromstring(response.content)
        items = []
        for item in root.iter("item"):
            title = (item.findtext("title") or "").strip()
            desc = re.sub(r"<[^>]+>", "", item.findtext("description") or "").strip()
            link = (item.findtext("link") or "").strip()
            pub = (item.findtext("pubDate") or "").strip()
            if match_fn and not match_fn(title, desc):
                continue
            items.append({
                "title": title,
                "summary": desc[:300] if desc else "",
                "pub_raw": pub,
                "sort_ts": pub_ts(pub),
                "pub": fmt_pubdate(pub) if pub else "",
                "url": link,
                "source": rss_source(url),
            })
        return items
    except Exception as exc:
        print(f"[rss] {url} error: {exc}")
        return []


def _extract_feed_source(title):
    parts = [p.strip() for p in (title or "").rsplit(" - ", 1)]
    if len(parts) == 2 and len(parts[1]) <= 32:
        return parts[0], parts[1]
    return title, ""


def _strip_feed_source_suffix(text, source, normalize_whitespace):
    cleaned = normalize_whitespace(text)
    src = normalize_whitespace(source)
    if not cleaned or not src:
        return cleaned
    pattern = re.compile(r"(?:\s+|[\-|:|窶｢])" + re.escape(src) + r"$", re.IGNORECASE)
    return pattern.sub("", cleaned).strip()


def parse_google_news_rss(
    query,
    *,
    headers,
    pub_ts,
    fmt_pubdate,
    normalize_whitespace,
    limit=12,
    hl="en-US",
    gl="US",
    ceid="US:en",
):
    url = (
        f"https://news.google.com/rss/search?q={quote_plus(query)}"
        f"&hl={quote_plus(hl)}&gl={quote_plus(gl)}&ceid={quote_plus(ceid)}"
    )
    try:
        response = requests.get(url, timeout=10, headers=headers)
        response.raise_for_status()
        root = ET.fromstring(response.content)
        items = []
        for item in root.iter("item"):
            raw_title = normalize_whitespace(item.findtext("title") or "")
            title, source = _extract_feed_source(raw_title)
            link = normalize_whitespace(item.findtext("link") or "")
            pub = normalize_whitespace(item.findtext("pubDate") or "")
            desc = re.sub(r"<[^>]+>", " ", item.findtext("description") or "")
            desc = normalize_whitespace(desc)
            desc = _strip_feed_source_suffix(desc, source, normalize_whitespace)
            if not title:
                continue
            items.append({
                "title": title,
                "summary": desc[:240],
                "pub_raw": pub,
                "sort_ts": pub_ts(pub),
                "pub": fmt_pubdate(pub) if pub else "",
                "url": link,
                "provider": source or "Google News",
            })
            if len(items) >= limit:
                break
        return items
    except Exception as exc:
        print(f"[topic_rss] {query} error: {exc}")
        return []
