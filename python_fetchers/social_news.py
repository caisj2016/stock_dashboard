import re
import xml.etree.ElementTree as ET
from html import unescape

import requests


def html_to_lines(html_text):
    cleaned = re.sub(r"<(script|style)[^>]*>.*?</\1>", "", html_text, flags=re.I | re.S)
    cleaned = re.sub(r"<br\s*/?>", "\n", cleaned, flags=re.I)
    cleaned = re.sub(r"</(p|div|section|article|li|h\d)>", "\n", cleaned, flags=re.I)
    cleaned = re.sub(r"<[^>]+>", "\n", cleaned)
    cleaned = unescape(cleaned)
    lines = []
    for line in cleaned.splitlines():
        line = re.sub(r"\s+", " ", line).strip()
        if line:
            lines.append(line)
    return lines


def clean_social_text(text):
    text = re.sub(r"\s+", " ", (text or "")).strip()
    text = re.sub(r"\s*https?://\S+\s*$", "", text)
    return text.strip(" -")


def build_social_item(source, text, pub, url, *, translate_to_zh, relative_ts):
    text = clean_social_text(text)
    if not text:
        return None
    title = text[:120] + ("..." if len(text) > 120 else "")
    summary = text[:360]
    return {
        "title": title,
        "title_zh": translate_to_zh(title),
        "summary": summary,
        "summary_zh": translate_to_zh(summary) if summary else "",
        "pub": pub,
        "pub_raw": pub,
        "sort_ts": relative_ts(pub),
        "url": url,
        "source": source,
    }


def parse_social_rss(url, source, *, headers, translate_to_zh, pub_ts, fmt_pubdate, limit=6, fallback_url=""):
    try:
        response = requests.get(url, timeout=10, headers=headers)
        response.raise_for_status()
        root = ET.fromstring(response.content)
        items = []
        for item in root.iter("item"):
            title = (item.findtext("title") or "").strip()
            desc = re.sub(r"<[^>]+>", " ", item.findtext("description") or "").strip()
            link = (item.findtext("link") or "").strip()
            pub = (item.findtext("pubDate") or "").strip()
            text = clean_social_text(desc or title)
            if not text:
                continue
            items.append({
                "title": text[:120] + ("..." if len(text) > 120 else ""),
                "title_zh": translate_to_zh(text[:120]),
                "summary": text[:360],
                "summary_zh": translate_to_zh(text[:360]),
                "pub_raw": pub,
                "sort_ts": pub_ts(pub),
                "pub": fmt_pubdate(pub) if pub else source,
                "url": link or fallback_url or url,
                "source": source,
            })
            if len(items) >= limit:
                break
        return items
    except Exception as exc:
        print(f"[{source.lower()}_feed] error: {exc}")
        return []


def guess_pubdate_from_url(url):
    match = re.search(r"/(20\d{2})/(\d{2})/(\d{2})/", str(url or ""))
    if not match:
        return ""
    year, month, day = match.groups()
    return f"{year}-{month}-{day} 00:00:00 +0900"


def extract_html_card_summary(html, href, *, normalize_whitespace, fallback_title=""):
    if not html or not href:
        return ""
    idx = html.find(href)
    if idx < 0:
        return ""
    window = html[idx: idx + 1400]
    text = re.sub(r"<[^>]+>", " ", window)
    text = unescape(normalize_whitespace(text))
    if fallback_title:
        text = text.replace(fallback_title, "").strip()
    parts = re.split(r"\s{2,}|(?<=[.!?])\s+", text)
    for part in parts:
        cleaned = normalize_whitespace(part)
        if 30 <= len(cleaned) <= 220 and href not in cleaned:
            return cleaned[:220]
    return ""


def parse_html_topic_page(
    url,
    source_name,
    *,
    headers,
    normalize_whitespace,
    guess_pubdate_from_url,
    extract_html_card_summary,
    limit=12,
):
    try:
        response = requests.get(url, timeout=10, headers=headers)
        response.raise_for_status()
        html = response.text or ""
        items = []
        seen = set()
        pattern = re.compile(r'<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>', re.IGNORECASE | re.DOTALL)
        for href, inner in pattern.findall(html):
            title = re.sub(r"<[^>]+>", " ", inner)
            title = unescape(normalize_whitespace(title))
            if len(title) < 24 or len(title) > 180:
                continue
            if href.startswith("/"):
                href = "https://www.japantimes.co.jp" + href
            if not href.startswith("http"):
                continue
            key = title.lower()
            if key in seen:
                continue
            seen.add(key)
            pub_raw = guess_pubdate_from_url(href)
            summary = extract_html_card_summary(html, href, fallback_title=title)
            items.append({
                "title": title,
                "summary": summary,
                "pub_raw": pub_raw,
                "sort_ts": 0,
                "pub": "",
                "url": href,
                "provider": source_name,
                "source": source_name,
            })
            if len(items) >= limit:
                break
        return items
    except Exception as exc:
        print(f"[topic_html] {url} error: {exc}")
        return []
