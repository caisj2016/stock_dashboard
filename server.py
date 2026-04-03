#!/usr/bin/env python3
"""
日本株ポートフォリオ看板 - ローカルサーバー
使い方: python3 server.py
ブラウザで http://localhost:5555 を開く
"""

import json
import os
import re
import threading
import time
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeoutError
from datetime import datetime
from email.utils import parsedate_to_datetime
from html import unescape
from urllib.parse import quote_plus

import requests
import yfinance as yf
from flask import Flask, jsonify, render_template, request
from flask_cors import CORS

BASE_DIR = os.path.dirname(__file__)
ENV_FILE = os.path.join(BASE_DIR, ".env")


def load_env_file(path):
    if not os.path.exists(path):
        return
    with open(path, "r", encoding="utf-8") as f:
        for raw_line in f:
            line = raw_line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            key = key.strip()
            value = value.strip().strip('"').strip("'")
            if key:
                os.environ.setdefault(key, value)


def env_bool(name, default=False):
    value = os.environ.get(name)
    if value is None:
        return default
    return value.strip().lower() not in {"0", "false", "no", "off"}


def env_int(name, default):
    value = os.environ.get(name)
    try:
        return int(str(value).strip()) if value is not None else default
    except Exception:
        return default


load_env_file(ENV_FILE)

app = Flask(__name__, static_folder="static", template_folder="templates")
CORS(app)
app.config["TEMPLATES_AUTO_RELOAD"] = env_bool("TEMPLATES_AUTO_RELOAD", True)
app.config["SEND_FILE_MAX_AGE_DEFAULT"] = env_int("SEND_FILE_MAX_AGE_DEFAULT", 0)

DATA_FILE = os.path.join(BASE_DIR, "portfolio.json")
YF_CACHE_DIR = os.path.join(BASE_DIR, ".yf_cache")
os.makedirs(YF_CACHE_DIR, exist_ok=True)
if hasattr(yf, "set_tz_cache_location"):
    yf.set_tz_cache_location(YF_CACHE_DIR)

STOCK_NAME_MAP = {
    "6758.T": "ソニーグループ",
    "7974.T": "任天堂",
    "9984.T": "ソフトバンクグループ",
    "7711.T": "助川電気工業",
    "3436.T": "SUMCO",
    "6330.T": "東洋エンジニアリング",
    "6762.T": "ＴＤＫ",
}

DEFAULT_PORTFOLIO = [
    {"code": "6758.T", "name": "ソニーグループ",       "shares": 0, "cost": 0, "status": "watch"},
    {"code": "7974.T", "name": "任天堂",              "shares": 0, "cost": 0, "status": "watch"},
    {"code": "9984.T", "name": "ソフトバンクグループ", "shares": 0, "cost": 0, "status": "watch"},
]

_cache = {}
_cache_lock = threading.Lock()
CACHE_TTL = env_int("QUOTE_CACHE_TTL", 60)
QUOTE_FETCH_WORKERS = env_int("QUOTE_FETCH_WORKERS", 6)
SCREENER_FETCH_WORKERS = env_int("SCREENER_FETCH_WORKERS", 8)
QUOTE_EXECUTOR = ThreadPoolExecutor(max_workers=max(2, QUOTE_FETCH_WORKERS))
SCREENER_EXECUTOR = ThreadPoolExecutor(max_workers=max(2, SCREENER_FETCH_WORKERS))

# Override startup defaults with ASCII-safe Unicode escapes so Japanese names
# stay correct even on terminals with a non-UTF-8 code page.
STOCK_NAME_MAP = {
    "6758.T": "\u30bd\u30cb\u30fc\u30b0\u30eb\u30fc\u30d7",
    "7974.T": "\u4efb\u5929\u5802",
    "9984.T": "\u30bd\u30d5\u30c8\u30d0\u30f3\u30af\u30b0\u30eb\u30fc\u30d7",
    "7711.T": "\u52a9\u5ddd\u96fb\u6c17\u5de5\u696d",
    "3436.T": "SUMCO",
    "6330.T": "\u6771\u6d0b\u30a8\u30f3\u30b8\u30cb\u30a2\u30ea\u30f3\u30b0",
    "6762.T": "\uff34\uff24\uff2b",
}

DEFAULT_PORTFOLIO = [
    {"code": "6758.T", "name": STOCK_NAME_MAP["6758.T"], "shares": 0, "cost": 0, "status": "watch"},
    {"code": "7974.T", "name": STOCK_NAME_MAP["7974.T"], "shares": 0, "cost": 0, "status": "watch"},
    {"code": "9984.T", "name": STOCK_NAME_MAP["9984.T"], "shares": 0, "cost": 0, "status": "watch"},
]

NEWS_CACHE = {}
NEWS_CACHE_TTL = env_int("NEWS_CACHE_TTL", 300)
TRUMP_CACHE_TTL = env_int("TRUMP_CACHE_TTL", 600)

HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/124.0 Safari/537.36"
    ),
    "Accept-Language": "en-US,en;q=0.9",
}


# ────────────────────────────────────────
#  Free translation  (Google Translate unofficial)
# ────────────────────────────────────────

def translate_to_zh(text: str) -> str:
    """Translate any text to Simplified Chinese using free Google endpoint."""
    if not text or not text.strip():
        return text
    try:
        url = "https://translate.googleapis.com/translate_a/single"
        params = {
            "client": "gtx",
            "sl": "auto",
            "tl": "zh-CN",
            "dt": "t",
            "q": text[:400],   # keep under limit
        }
        r = requests.get(url, params=params, timeout=8, headers=HEADERS)
        r.raise_for_status()
        data = r.json()
        # result is nested list: [[["translated","original",...], ...], ...]
        parts = []
        for block in data[0]:
            if block and block[0]:
                parts.append(block[0])
        return "".join(parts)
    except Exception:
        return text   # fallback: return original


# ────────────────────────────────────────
#  Portfolio helpers
# ────────────────────────────────────────

def load_portfolio():
    if os.path.exists(DATA_FILE):
        with open(DATA_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
            for item in data:
                item.setdefault("status", "holding" if item.get("shares", 0) > 0 else "watch")
                item["name"] = resolve_stock_name(item.get("code", ""), item.get("name"))
            return data
    return DEFAULT_PORTFOLIO


def save_portfolio(data):
    with open(DATA_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)


def _is_symbol_like_name(name, code):
    if not name:
        return True
    normalized = re.sub(r"[\s._-]", "", str(name).upper())
    normalized_code = re.sub(r"[\s._-]", "", str(code).upper())
    return normalized == normalized_code


def _clean_company_name(name):
    if not name:
        return None
    cleaned = str(name).strip()
    if not cleaned:
        return None
    return re.sub(r"\s+", " ", cleaned)


def fetch_company_name(code):
    symbol = (code or "").upper()
    if not symbol:
        return code

    mapped = STOCK_NAME_MAP.get(symbol)
    if mapped:
        return mapped

    try:
        info = yf.Ticker(symbol).info or {}
    except Exception:
        info = {}

    for key in ("longName", "shortName", "displayName"):
        candidate = _clean_company_name(info.get(key))
        if candidate and not _is_symbol_like_name(candidate, symbol):
            return candidate

    return symbol


def resolve_stock_name(code, current_name=None):
    if current_name and str(current_name).strip() and not _is_symbol_like_name(current_name, code):
        return str(current_name).strip()
    fetched_name = fetch_company_name(code)
    if fetched_name and not _is_symbol_like_name(fetched_name, code):
        return fetched_name
    return code


# ────────────────────────────────────────
#  Quote fetcher
# ────────────────────────────────────────

def fetch_quote(symbol):
    now = time.time()
    with _cache_lock:
        if symbol in _cache and now - _cache[symbol]["ts"] < CACHE_TTL:
            return _cache[symbol]["data"]
    try:
        tk = yf.Ticker(symbol)
        info = tk.fast_info
        # Use a lighter sparkline payload so dashboard refreshes stay snappy.
        hist = tk.history(period="5d", interval="30m")
        price = float(info.last_price) if info.last_price else None
        prev_close = float(info.previous_close) if info.previous_close else None
        closes = []
        if not hist.empty:
            closes = [round(v, 2) if v == v else None for v in hist["Close"].tail(40).tolist()]
            if price is None and closes:
                price = closes[-1]
        change = round(price - prev_close, 2) if price and prev_close else None
        pct = round((change / prev_close) * 100, 2) if change and prev_close else None
        volume = int(info.three_month_average_volume or 0)
        market_state = "CLOSED"
        now_jst = datetime.utcnow()
        hour_jst = (now_jst.hour + 9) % 24
        t = hour_jst * 60 + now_jst.minute
        dow = now_jst.weekday()
        if dow < 5 and ((9*60 <= t < 11*60+30) or (12*60+30 <= t < 15*60+30)):
            market_state = "REGULAR"
        result = {
            "symbol": symbol, "price": price, "prev_close": prev_close,
            "change": change, "pct": pct, "volume": volume,
            "closes": closes, "market_state": market_state,
            "updated": datetime.now().strftime("%H:%M:%S"),
        }
        with _cache_lock:
            _cache[symbol] = {"ts": time.time(), "data": result}
        return result
    except Exception as e:
        return {"symbol": symbol, "error": str(e)}


# ────────────────────────────────────────
#  Stock news  (yfinance → translate)
# ────────────────────────────────────────

def fetch_stock_news(symbol):
    cache_key = f"news_{symbol}"
    now = time.time()
    if cache_key in NEWS_CACHE and now - NEWS_CACHE[cache_key]["ts"] < NEWS_CACHE_TTL:
        return NEWS_CACHE[cache_key]["data"]
    try:
        tk = yf.Ticker(symbol)
        raw = tk.news or []
        items = []
        for n in raw[:8]:
            ct = n.get("content", {})
            title    = ct.get("title")    or n.get("title", "")
            summary  = ct.get("summary")  or n.get("summary", "")
            pub_raw  = ct.get("pubDate")  or str(n.get("providerPublishTime", ""))
            provider = (ct.get("provider") or {}).get("displayName") or n.get("publisher", "")
            url      = (ct.get("canonicalUrl") or {}).get("url") or n.get("link", "")

            # parse publish time → friendly string
            pub_str = _fmt_pubdate(pub_raw)

            if title:
                title_zh   = translate_to_zh(title)
                summary_zh = translate_to_zh(str(summary)[:300]) if summary else ""
                items.append({
                    "title":    title_zh,
                    "title_en": title,
                    "summary":  summary_zh,
                    "pub":      pub_str,
                    "provider": provider,
                    "url":      url,
                })
        NEWS_CACHE[cache_key] = {"ts": now, "data": items}
        return items
    except Exception as e:
        print(f"[stock_news] {symbol} error: {e}")
        return []


def _fmt_pubdate(raw):
    """Parse RFC-2822 or Unix timestamp → 'MM-DD HH:MM' in JST."""
    try:
        if not raw:
            return ""
        # unix timestamp
        if str(raw).isdigit():
            dt = datetime.utcfromtimestamp(int(raw))
        else:
            dt = parsedate_to_datetime(raw).replace(tzinfo=None)
        # convert UTC → JST (+9)
        from datetime import timedelta
        dt_jst = dt + timedelta(hours=9)
        return dt_jst.strftime("%m-%d %H:%M")
    except Exception:
        return str(raw)[:16]


# ────────────────────────────────────────
#  Trump news  (RSS → filter → translate)
# ────────────────────────────────────────

TRUMP_RSS_FEEDS = [
    "https://feeds.reuters.com/reuters/politicsNews",
    "https://feeds.apnews.com/rss/apf-politics",
    "https://thehill.com/rss/syndicator/19110",
    "http://feeds.bbci.co.uk/news/world/us_and_canada/rss.xml",
]

TRUMP_TRUTH_URL = "https://www.trumpstruth.org/feed"
TRUMP_X_RSS_URL = "https://rsshub.app/twitter/user/realDonaldTrump"

TRUMP_KW = re.compile(
    r"trump|tariff|white house|trade war|truth social|mar.a.lago"
    r"|executive order|sanctions|nato|iran|china tariff|\u5173\u7a0e|\u30c8\u30e9\u30f3\u30d7",
    re.IGNORECASE,
)


def _pub_ts(raw):
    try:
        if not raw:
            return 0
        if str(raw).isdigit():
            return int(raw)
        return int(parsedate_to_datetime(raw).timestamp())
    except Exception:
        return 0


def _relative_ts(raw):
    try:
        now = int(time.time())
        s = (raw or "").strip().lower()
        if not s:
            return 0
        if s in {"just now", "now"}:
            return now
        m = re.match(r"(\d+)\s*([smhd])", s)
        if not m:
            return now
        n = int(m.group(1))
        mult = {"s": 1, "m": 60, "h": 3600, "d": 86400}[m.group(2)]
        return now - n * mult
    except Exception:
        return int(time.time())


def _parse_rss(url):
    try:
        r = requests.get(url, timeout=10, headers=HEADERS)
        root = ET.fromstring(r.content)
        items = []
        for item in root.iter("item"):
            title = (item.findtext("title") or "").strip()
            desc = re.sub(r"<[^>]+>", "", item.findtext("description") or "").strip()
            link = (item.findtext("link") or "").strip()
            pub = (item.findtext("pubDate") or "").strip()
            if TRUMP_KW.search(title) or TRUMP_KW.search(desc[:200]):
                items.append({
                    "title": title,
                    "summary": desc[:300] if desc else "",
                    "pub_raw": pub,
                    "sort_ts": _pub_ts(pub),
                    "pub": _fmt_pubdate(pub) if pub else "",
                    "url": link,
                    "source": _rss_source(url),
                })
        return items
    except Exception as e:
        print(f"[rss] {url} error: {e}")
        return []


def _rss_source(url):
    host = url.split("/")[2]
    for part in ["reuters", "apnews", "thehill", "bbc"]:
        if part in host:
            return part.upper()
    return host.replace("www.", "").replace("feeds.", "")


def _html_to_lines(html_text):
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


def _clean_social_text(text):
    text = re.sub(r"\s+", " ", (text or "")).strip()
    text = re.sub(r"\s*https?://\S+\s*$", "", text)
    return text.strip(" -")


def _build_social_item(source, text, pub, url):
    text = _clean_social_text(text)
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
        "sort_ts": _relative_ts(pub),
        "url": url,
        "source": source,
    }


MARKET_KEYWORDS = {
    "tariff": ["tariff", "tariffs", "关税", "征税"],
    "china": ["china", "chinese", "中国"],
    "trade": ["trade", "trade war", "贸易", "出口", "进口"],
    "chips": ["chip", "chips", "semiconductor", "semiconductors", "半导体", "芯片", "tsmc", "nvidia"],
    "energy": ["oil", "gas", "energy", "drilling", "opec", "原油", "天然气", "能源"],
    "rates": ["fed", "interest rate", "rates", "inflation", "cpi", "powell", "利率", "通胀", "美联储"],
    "sanctions": ["sanction", "sanctions", "制裁"],
    "defense": ["nato", "missile", "military", "defense", "国防", "军工"],
    "autos": ["auto", "autos", "car", "cars", "toyota", "tesla", "汽车"],
    "tax": ["tax", "taxes", "减税", "税收"],
}

MARKET_IMPACT_LABELS = {
    "tariff": "关税",
    "china": "中国",
    "trade": "贸易",
    "chips": "半导体",
    "energy": "能源",
    "rates": "利率",
    "sanctions": "制裁",
    "defense": "军工",
    "autos": "汽车",
    "tax": "税收",
}


def _market_tags(text):
    hay = (text or "").lower()
    tags = []
    for key, words in MARKET_KEYWORDS.items():
        if any(word.lower() in hay for word in words):
            tags.append(key)
    return tags


def _market_score(item):
    text = " ".join([
        item.get("title", ""),
        item.get("summary", ""),
        item.get("title_zh", ""),
        item.get("summary_zh", ""),
    ])
    tags = _market_tags(text)
    score = len(tags) * 10

    if item.get("source") in {"TRUTH", "X"}:
        score += 8
    if "tariff" in tags or "trade" in tags or "china" in tags:
        score += 10
    if "chips" in tags or "energy" in tags or "rates" in tags:
        score += 8
    if any(word in text.lower() for word in ["market", "stocks", "equity", "earnings", "shares", "股市", "市场"]):
        score += 4
    return score, tags


def _short_summary(item, tags):
    base = item.get("summary_zh") or item.get("title_zh") or item.get("summary") or item.get("title") or ""
    base = re.sub(r"\s+", " ", base).strip()
    if not base:
        return ""

    if tags:
        prefix = "、".join(MARKET_IMPACT_LABELS[t] for t in tags[:3])
        return f"重点: {prefix}。{base[:90]}"
    return base[:100]


def _decorate_market_item(item):
    score, tags = _market_score(item)
    item["market_score"] = score
    item["market_tags"] = [MARKET_IMPACT_LABELS[t] for t in tags[:3]]
    item["brief"] = _short_summary(item, tags)
    item["is_market_relevant"] = score >= 10
    return item


def _parse_social_rss(url, source, limit=6, fallback_url=""):
    try:
        r = requests.get(url, timeout=10, headers=HEADERS)
        r.raise_for_status()
        root = ET.fromstring(r.content)
        items = []
        for item in root.iter("item"):
            title = (item.findtext("title") or "").strip()
            desc = re.sub(r"<[^>]+>", " ", item.findtext("description") or "").strip()
            link = (item.findtext("link") or "").strip()
            pub = (item.findtext("pubDate") or "").strip()
            text = _clean_social_text(desc or title)
            if not text:
                continue
            items.append({
                "title": text[:120] + ("..." if len(text) > 120 else ""),
                "title_zh": translate_to_zh(text[:120]),
                "summary": text[:360],
                "summary_zh": translate_to_zh(text[:360]),
                "pub_raw": pub,
                "sort_ts": _pub_ts(pub),
                "pub": _fmt_pubdate(pub) if pub else source,
                "url": link or fallback_url or url,
                "source": source,
            })
            if len(items) >= limit:
                break
        return items
    except Exception as e:
        print(f"[{source.lower()}_feed] error: {e}")
        return []


def fetch_truth_social_posts():
    return _parse_social_rss(
        TRUMP_TRUTH_URL,
        "TRUTH",
        limit=6,
        fallback_url="https://truthsocialapp.com/@realDonaldTrump",
    )


def fetch_x_posts():
    return _parse_social_rss(
        TRUMP_X_RSS_URL,
        "X",
        limit=6,
        fallback_url="https://x.com/realDonaldTrump",
    )


def fetch_trump_news():
    now = time.time()
    if "trump" in NEWS_CACHE and now - NEWS_CACHE["trump"]["ts"] < TRUMP_CACHE_TTL:
        return NEWS_CACHE["trump"]["data"]

    all_items = []
    all_items.extend(fetch_truth_social_posts())
    all_items.extend(fetch_x_posts())
    for feed in TRUMP_RSS_FEEDS:
        all_items.extend(_parse_rss(feed))

    seen, unique = set(), []
    for it in all_items:
        key = (it.get("source", "") + "|" + it["title"][:60]).lower()
        if key not in seen:
            seen.add(key)
            unique.append(it)

    unique.sort(key=lambda x: x.get("sort_ts", 0), reverse=True)
    top = unique[:12]

    for it in top:
        if not it.get("title_zh"):
            it["title_zh"] = translate_to_zh(it["title"])
        if it.get("summary") and not it.get("summary_zh"):
            it["summary_zh"] = translate_to_zh(it["summary"])
        _decorate_market_item(it)

    relevant = [it for it in top if it.get("is_market_relevant")]
    relevant.sort(key=lambda x: (x.get("market_score", 0), x.get("sort_ts", 0)), reverse=True)
    others = [it for it in top if not it.get("is_market_relevant")]
    others.sort(key=lambda x: x.get("sort_ts", 0), reverse=True)
    top = (relevant[:8] + others[:4])[:12]

    NEWS_CACHE["trump"] = {"ts": now, "data": top}
    return top


TOPIC_DIGEST_TTL = 600
TOPIC_CONFIG = {
    "nikkei": {
        "title": "日经今天怎么看",
        "query": "Nikkei OR 日経 average Japan stocks BOJ USDJPY Reuters Bloomberg",
        "drivers": {
            "日元汇率": ["usd/jpy", "yen", "jpy", "dollar", "fx", "汇率", "円", "為替"],
            "日本央行": ["boj", "bank of japan", "ueda", "rate", "rates", "yield", "加息", "降息", "日银"],
            "出口板块": ["toyota", "sony", "nintendo", "export", "autos", "exporters", "出口", "汽车"],
            "财报动向": ["earnings", "guidance", "profit", "results", "业绩", "财报"],
            "风险偏好": ["wall street", "nasdaq", "s&p 500", "futures", "risk", "美股", "期货"],
        },
        "positive": ["surge", "gain", "gains", "rise", "rally", "beat", "up", "higher", "weaker yen", "record"],
        "negative": ["fall", "falls", "drop", "drops", "slump", "miss", "down", "lower", "stronger yen", "selloff"],
        "quote_symbol": "^N225",
    },
    "semiconductor": {
        "title": "半导体今天怎么看",
        "query": "semiconductor OR chip OR AI server OR HBM OR TSMC OR Nvidia OR ASML OR Tokyo Electron OR SUMCO Reuters Bloomberg",
        "drivers": {
            "AI 需求": ["ai", "nvidia", "gpu", "ai server", "datacenter", "blackwell"],
            "先进制程": ["tsmc", "2nm", "3nm", "wafer", "foundry", "先进制程", "晶圆厂"],
            "设备资本开支": ["asml", "tokyo electron", "screen", "capex", "equipment", "lithography", "设备"],
            "存储与 HBM": ["hbm", "dram", "nand", "memory", "sk hynix", "micron", "samsung"],
            "政策限制": ["china", "export control", "sanction", "restriction", "tariff", "补贴", "限制"],
        },
        "positive": ["surge", "gain", "gains", "rise", "rally", "beat", "record", "expand", "strong demand", "boost"],
        "negative": ["fall", "falls", "drop", "drops", "slump", "cut", "delay", "weak demand", "restriction", "tariff"],
        "related_symbols": ["3436.T", "6762.T"],
    },
}

SCREENER_UNIVERSE_CORE_45 = [
    {"code": "1332.T", "name": "ニッスイ"},
    {"code": "1605.T", "name": "ＩＮＰＥＸ"},
    {"code": "1802.T", "name": "大林組"},
    {"code": "1925.T", "name": "大和ハウス工業"},
    {"code": "2914.T", "name": "日本たばこ産業"},
    {"code": "3382.T", "name": "セブン＆アイ・ホールディングス"},
    {"code": "4063.T", "name": "信越化学工業"},
    {"code": "4502.T", "name": "武田薬品工業"},
    {"code": "4568.T", "name": "第一三共"},
    {"code": "6098.T", "name": "リクルートホールディングス"},
    {"code": "6501.T", "name": "日立製作所"},
    {"code": "6503.T", "name": "三菱電機"},
    {"code": "6752.T", "name": "パナソニック　ホールディングス"},
    {"code": "6758.T", "name": "ソニーグループ"},
    {"code": "6762.T", "name": "ＴＤＫ"},
    {"code": "6857.T", "name": "アドバンテスト"},
    {"code": "6902.T", "name": "デンソー"},
    {"code": "6954.T", "name": "ファナック"},
    {"code": "6981.T", "name": "村田製作所"},
    {"code": "7201.T", "name": "日産自動車"},
    {"code": "7203.T", "name": "トヨタ自動車"},
    {"code": "7267.T", "name": "本田技研工業"},
    {"code": "7733.T", "name": "オリンパス"},
    {"code": "7741.T", "name": "ＨＯＹＡ"},
    {"code": "7751.T", "name": "キヤノン"},
    {"code": "7974.T", "name": "任天堂"},
    {"code": "8001.T", "name": "伊藤忠商事"},
    {"code": "8002.T", "name": "丸紅"},
    {"code": "8031.T", "name": "三井物産"},
    {"code": "8035.T", "name": "東京エレクトロン"},
    {"code": "8058.T", "name": "三菱商事"},
    {"code": "8306.T", "name": "三菱ＵＦＪフィナンシャル・グループ"},
    {"code": "8316.T", "name": "三井住友フィナンシャルグループ"},
    {"code": "8411.T", "name": "みずほフィナンシャルグループ"},
    {"code": "8591.T", "name": "オリックス"},
    {"code": "8766.T", "name": "東京海上ホールディングス"},
    {"code": "9020.T", "name": "東日本旅客鉄道"},
    {"code": "9432.T", "name": "日本電信電話"},
    {"code": "9433.T", "name": "ＫＤＤＩ"},
    {"code": "9434.T", "name": "ソフトバンク"},
    {"code": "9501.T", "name": "東京電力ホールディングス"},
    {"code": "9503.T", "name": "関西電力"},
    {"code": "9735.T", "name": "セコム"},
    {"code": "9983.T", "name": "ファーストリテイリング"},
    {"code": "9984.T", "name": "ソフトバンクグループ"},
]

SCREENER_UNIVERSE_TOPIX_CORE_30_CODES = {
    "1605.T", "1925.T", "2914.T", "4063.T", "4502.T", "6098.T", "6501.T", "6758.T",
    "6762.T", "6857.T", "6954.T", "6981.T", "7203.T", "7741.T", "7974.T", "8001.T",
    "8002.T", "8031.T", "8035.T", "8058.T", "8306.T", "8316.T", "8411.T", "8591.T",
    "8766.T", "9020.T", "9432.T", "9433.T", "9983.T", "9984.T",
}

SCREENER_UNIVERSE_TOPIX_CORE_30 = [
    stock for stock in SCREENER_UNIVERSE_CORE_45
    if stock["code"] in SCREENER_UNIVERSE_TOPIX_CORE_30_CODES
]

SCREENER_UNIVERSE_MAP = {
    "core45": {
        "label": "核心45",
        "description": "当前维护最快的核心股票池",
        "items": SCREENER_UNIVERSE_CORE_45,
    },
    "nikkei225": {
        "label": "Nikkei 225样本45",
        "description": "当前版本先用 45 只日经核心样本",
        "items": SCREENER_UNIVERSE_CORE_45,
    },
    "topixcore": {
        "label": "TOPIX Core 30",
        "description": "更偏大盘权重股的 30 只核心池",
        "items": SCREENER_UNIVERSE_TOPIX_CORE_30,
    },
}

HISTORY_CACHE_TTL = env_int("HISTORY_CACHE_TTL", 900)
HISTORY_FETCH_TIMEOUT = env_int("HISTORY_FETCH_TIMEOUT", 12)
YF_HISTORY_EXECUTOR = ThreadPoolExecutor(max_workers=env_int("HISTORY_FETCH_WORKERS", 4))


def _normalize_whitespace(text):
    return re.sub(r"\s+", " ", (text or "")).strip()


def _contains_term(text, keyword):
    hay = (text or "").lower()
    needle = (keyword or "").lower().strip()
    if not needle:
        return False
    if re.search(r"[\u4e00-\u9fff\u3040-\u30ff]", needle):
        return needle in hay
    pattern = r"(?<![a-z0-9])" + re.escape(needle) + r"(?![a-z0-9])"
    return re.search(pattern, hay) is not None


def _extract_feed_source(title):
    parts = [p.strip() for p in (title or "").rsplit(" - ", 1)]
    if len(parts) == 2 and len(parts[1]) <= 32:
        return parts[0], parts[1]
    return title, ""


def _parse_google_news_rss(query, limit=12):
    url = f"https://news.google.com/rss/search?q={quote_plus(query)}&hl=en-US&gl=US&ceid=US:en"
    try:
        r = requests.get(url, timeout=10, headers=HEADERS)
        r.raise_for_status()
        root = ET.fromstring(r.content)
        items = []
        for item in root.iter("item"):
            raw_title = _normalize_whitespace(item.findtext("title") or "")
            title, source = _extract_feed_source(raw_title)
            link = _normalize_whitespace(item.findtext("link") or "")
            pub = _normalize_whitespace(item.findtext("pubDate") or "")
            desc = re.sub(r"<[^>]+>", " ", item.findtext("description") or "")
            desc = _normalize_whitespace(desc)
            if not title:
                continue
            items.append({
                "title": title,
                "summary": desc[:240],
                "pub_raw": pub,
                "sort_ts": _pub_ts(pub),
                "pub": _fmt_pubdate(pub) if pub else "",
                "url": link,
                "provider": source or "Google News",
            })
            if len(items) >= limit:
                break
        return items
    except Exception as e:
        print(f"[topic_rss] {query} error: {e}")
        return []


def _score_topic_item(item, topic_cfg):
    text = " ".join([item.get("title", ""), item.get("summary", "")]).lower()
    score = 0
    hits = []
    for label, keywords in topic_cfg.get("drivers", {}).items():
        if any(_contains_term(text, keyword) for keyword in keywords):
            score += 10
            hits.append(label)
    provider = (item.get("provider") or "").lower()
    if provider in {"reuters", "bloomberg", "nikkei asia", "cnbc"}:
        score += 4
    if "update" in text or "outlook" in text or "forecast" in text:
        score += 2
    item["driver_hits"] = hits
    item["digest_score"] = score
    return item


def _detect_digest_tone(items, topic_cfg):
    score = 0
    texts = [" ".join([it.get("title", ""), it.get("summary", "")]).lower() for it in items]
    for text in texts:
        score += sum(1 for word in topic_cfg.get("positive", []) if _contains_term(text, word))
        score -= sum(1 for word in topic_cfg.get("negative", []) if _contains_term(text, word))

    quote_symbol = topic_cfg.get("quote_symbol")
    if quote_symbol:
        try:
            pct = fetch_quote(quote_symbol).get("pct")
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


def _build_digest_summary(topic, tone, drivers):
    lead = {
        "nikkei": {
            "偏多": "今天先看顺风因素，日经更像是由汇率和风险偏好带动。",
            "偏空": "今天先看压制因素，日经更像是被汇率和避险情绪拖累。",
            "震荡": "今天更像信息拉扯市，暂时没有单一主线完全占优。",
        },
        "semiconductor": {
            "偏多": "今天半导体线索偏积极，资金更关注需求与资本开支。",
            "偏空": "今天半导体线索偏谨慎，市场更在意限制与需求波动。",
            "震荡": "今天半导体消息偏分化，强弱信号同时存在。",
        },
    }
    reason = "、".join(drivers[:3]) if drivers else "暂无单一主线"
    return f"{lead.get(topic, {}).get(tone, '')} 目前最值得看的驱动是：{reason}。"


def _translate_digest_items(items):
    for item in items:
        item["title_zh"] = translate_to_zh(item.get("title", ""))
        brief_src = item.get("summary") or item.get("title", "")
        item["brief"] = translate_to_zh(brief_src[:110]) if brief_src else ""
    return items


def fetch_topic_digest(topic):
    topic_key = (topic or "").strip().lower()
    if topic_key not in TOPIC_CONFIG:
        return {"error": "unknown topic"}

    cache_key = f"digest_{topic_key}"
    now = time.time()
    if cache_key in NEWS_CACHE and now - NEWS_CACHE[cache_key]["ts"] < TOPIC_DIGEST_TTL:
        return NEWS_CACHE[cache_key]["data"]

    cfg = TOPIC_CONFIG[topic_key]
    items = _parse_google_news_rss(cfg["query"], limit=12)
    seen = set()
    deduped = []
    for item in items:
        key = (item.get("title", "")[:80] + "|" + item.get("provider", "")).lower()
        if key in seen:
            continue
        seen.add(key)
        deduped.append(_score_topic_item(item, cfg))

    deduped.sort(key=lambda x: (x.get("digest_score", 0), x.get("sort_ts", 0)), reverse=True)
    top_items = _translate_digest_items(deduped[:3])

    driver_counts = {}
    for item in deduped[:6]:
        for label in item.get("driver_hits", []):
            driver_counts[label] = driver_counts.get(label, 0) + 1
    drivers = [label for label, _ in sorted(driver_counts.items(), key=lambda kv: (-kv[1], kv[0]))[:3]]

    tone, tone_class = _detect_digest_tone(top_items, cfg)
    result = {
        "topic": topic_key,
        "title": cfg["title"],
        "tone": tone,
        "tone_class": tone_class,
        "summary": _build_digest_summary(topic_key, tone, drivers),
        "drivers": drivers,
        "items": top_items,
        "updated": datetime.now().strftime("%H:%M"),
    }
    NEWS_CACHE[cache_key] = {"ts": now, "data": result}
    return result


def fetch_history(symbol, period="6mo", interval="1d"):
    cache_key = f"hist_{symbol}_{period}_{interval}"
    now = time.time()
    if cache_key in NEWS_CACHE and now - NEWS_CACHE[cache_key]["ts"] < HISTORY_CACHE_TTL:
        return NEWS_CACHE[cache_key]["data"]
    try:
        future = YF_HISTORY_EXECUTOR.submit(
            lambda: yf.Ticker(symbol).history(period=period, interval=interval, auto_adjust=False)
        )
        hist = future.result(timeout=HISTORY_FETCH_TIMEOUT)
    except FutureTimeoutError:
        hist = None
    except Exception:
        hist = None
    NEWS_CACHE[cache_key] = {"ts": now, "data": hist}
    return hist


def _safe_float(value):
    try:
        return float(value)
    except Exception:
        return None


def _calc_rsi(closes, period=14):
    if len(closes) < period + 1:
        return None
    gains, losses = [], []
    for i in range(1, len(closes)):
        delta = closes[i] - closes[i - 1]
        gains.append(max(delta, 0))
        losses.append(max(-delta, 0))
    avg_gain = sum(gains[-period:]) / period
    avg_loss = sum(losses[-period:]) / period
    if avg_loss == 0:
        return 100.0
    rs = avg_gain / avg_loss
    return 100 - (100 / (1 + rs))


def _ema(values, period):
    if len(values) < period:
        return []
    alpha = 2 / (period + 1)
    ema_vals = [sum(values[:period]) / period]
    for value in values[period:]:
        ema_vals.append((value - ema_vals[-1]) * alpha + ema_vals[-1])
    return ema_vals


def _calc_macd(closes):
    if len(closes) < 35:
        return {}
    ema12 = _ema(closes, 12)
    ema26 = _ema(closes, 26)
    if not ema12 or not ema26:
        return {}
    pad = len(ema12) - len(ema26)
    diffs = [ema12[i + pad] - ema26[i] for i in range(len(ema26))]
    signal = _ema(diffs, 9)
    if not signal:
        return {}
    pad2 = len(diffs) - len(signal)
    macd_line = diffs[-1]
    signal_line = signal[-1]
    prev_macd = diffs[-2] if len(diffs) >= 2 else None
    prev_signal = signal[-2] if len(signal) >= 2 else None
    return {
        "macd": macd_line,
        "signal": signal_line,
        "hist": macd_line - signal_line,
        "bull_cross": prev_macd is not None and prev_signal is not None and prev_macd <= prev_signal and macd_line > signal_line,
        "macd_series": diffs[-20:],
        "signal_series": signal[-20:],
        "hist_series": [(diffs[i + pad2] - signal[i]) for i in range(len(signal))][-20:],
    }


def _sma_series(values, period):
    result = []
    for i in range(len(values)):
        if i + 1 < period:
            result.append(None)
        else:
            window = values[i + 1 - period:i + 1]
            result.append(sum(window) / period)
    return result


def _calc_macd_full_series(closes):
    if len(closes) < 35:
        size = len(closes)
        return {"macd": [None] * size, "signal": [None] * size, "hist": [None] * size}

    ema12 = _ema(closes, 12)
    ema26 = _ema(closes, 26)
    if not ema12 or not ema26:
        size = len(closes)
        return {"macd": [None] * size, "signal": [None] * size, "hist": [None] * size}

    pad = len(ema12) - len(ema26)
    diffs = [ema12[i + pad] - ema26[i] for i in range(len(ema26))]
    signal = _ema(diffs, 9)
    if not signal:
        size = len(closes)
        return {"macd": [None] * size, "signal": [None] * size, "hist": [None] * size}

    pad2 = len(diffs) - len(signal)
    macd_aligned = [None] * (len(closes) - len(diffs)) + diffs
    signal_aligned = [None] * (len(closes) - len(signal)) + signal
    hist_values = [(diffs[i + pad2] - signal[i]) for i in range(len(signal))]
    hist_aligned = [None] * (len(closes) - len(hist_values)) + hist_values
    return {"macd": macd_aligned, "signal": signal_aligned, "hist": hist_aligned}


def _group_ohlcv(rows, chunk_size):
    grouped = []
    for i in range(0, len(rows), chunk_size):
        chunk = rows[i:i + chunk_size]
        if not chunk:
            continue
        grouped.append({
            "date": chunk[-1]["date"],
            "timestamp": chunk[-1]["timestamp"],
            "open": chunk[0]["open"],
            "high": max(item["high"] for item in chunk),
            "low": min(item["low"] for item in chunk),
            "close": chunk[-1]["close"],
            "volume": sum(item["volume"] for item in chunk),
        })
    return grouped


def _compress_ohlcv_rows(rows, limit):
    if len(rows) <= limit:
        return rows
    chunk_size = max(2, len(rows) // limit + (1 if len(rows) % limit else 0))
    return _group_ohlcv(rows, chunk_size)


def build_chart_history(symbol, interval="D"):
    symbol = (symbol or "").strip().upper()
    if not symbol:
        raise ValueError("missing symbol")

    configs = {
        "D": {"period": "6mo", "fetch_interval": "1d", "label": "日线", "limit": 90},
        "W": {"period": "2y", "fetch_interval": "1wk", "label": "周线", "limit": 90},
        "M": {"period": "5y", "fetch_interval": "1mo", "label": "月线", "limit": 90},
        "60": {"period": "60d", "fetch_interval": "60m", "label": "1小时", "limit": 80},
        "15": {"period": "30d", "fetch_interval": "15m", "label": "15分钟", "limit": 80},
        "240": {"period": "60d", "fetch_interval": "60m", "label": "4小时", "limit": 80, "group": 4},
    }
    cfg = configs.get(interval, configs["D"])
    hist = fetch_history(symbol, period=cfg["period"], interval=cfg["fetch_interval"])
    if hist is None or getattr(hist, "empty", True):
        return {
            "ok": False,
            "symbol": symbol,
            "interval": interval,
            "label": cfg["label"],
            "error": "当前环境无法获取历史行情，请稍后重试。",
        }

    rows = []
    for idx, row in hist.iterrows():
        open_v = _safe_float(row.get("Open"))
        high_v = _safe_float(row.get("High"))
        low_v = _safe_float(row.get("Low"))
        close_v = _safe_float(row.get("Close"))
        volume_v = _safe_float(row.get("Volume")) or 0
        if None in (open_v, high_v, low_v, close_v):
            continue
        date_label = idx.strftime("%Y-%m-%d") if cfg["fetch_interval"] in {"1d", "1wk", "1mo"} else idx.strftime("%m-%d %H:%M")
        timestamp_ms = int(idx.timestamp() * 1000)
        rows.append({
            "date": date_label,
            "timestamp": timestamp_ms,
            "open": round(open_v, 2),
            "high": round(high_v, 2),
            "low": round(low_v, 2),
            "close": round(close_v, 2),
            "volume": int(volume_v),
        })

    if cfg.get("group"):
        rows = _group_ohlcv(rows, cfg["group"])
    rows = _compress_ohlcv_rows(rows, cfg["limit"])

    if len(rows) < 12:
        return {
            "ok": False,
            "symbol": symbol,
            "interval": interval,
            "label": cfg["label"],
            "error": "可用历史数据不足，暂时无法绘制图表。",
        }

    closes = [row["close"] for row in rows]
    volumes = [row["volume"] for row in rows]
    ma5 = _sma_series(closes, 5)
    ma20 = _sma_series(closes, 20)
    macd_full = _calc_macd_full_series(closes)
    rsi14 = _calc_rsi(closes, 14)
    prev_close = closes[-2] if len(closes) >= 2 else None
    change_pct = ((closes[-1] - prev_close) / prev_close * 100) if prev_close else None
    avg_volume_5 = sum(volumes[-5:]) / 5 if len(volumes) >= 5 else None
    volume_ratio = (volumes[-1] / avg_volume_5) if avg_volume_5 else None

    return {
        "ok": True,
        "symbol": symbol,
        "name": resolve_stock_name(symbol, symbol),
        "interval": interval,
        "label": cfg["label"],
        "dates": [row["date"] for row in rows],
        "timestamps": [row["timestamp"] for row in rows],
        "opens": [row["open"] for row in rows],
        "highs": [row["high"] for row in rows],
        "lows": [row["low"] for row in rows],
        "closes": closes,
        "volumes": volumes,
        "ma5": [round(v, 2) if v is not None else None for v in ma5],
        "ma20": [round(v, 2) if v is not None else None for v in ma20],
        "macd": [round(v, 4) if v is not None else None for v in macd_full["macd"]],
        "signal": [round(v, 4) if v is not None else None for v in macd_full["signal"]],
        "hist": [round(v, 4) if v is not None else None for v in macd_full["hist"]],
        "price": round(closes[-1], 2),
        "change_pct": round(change_pct, 2) if change_pct is not None else None,
        "rsi14": round(rsi14, 2) if rsi14 is not None else None,
        "volume_ratio": round(volume_ratio, 2) if volume_ratio is not None else None,
        "updated": datetime.now().strftime("%H:%M"),
    }


def get_index_quotes_data():
    symbols = {"NI225": "^N225", "TOPIX": "1306.T"}
    raw = list(QUOTE_EXECUTOR.map(fetch_quote, symbols.values()))
    result = {}
    for (name, _), quote in zip(symbols.items(), raw):
        result[name] = {
            "price": quote.get("price"),
            "change": quote.get("change"),
            "pct": quote.get("pct"),
        }
    return result


def get_portfolio_quotes():
    portfolio = load_portfolio()
    raw_quotes = list(QUOTE_EXECUTOR.map(lambda stock: fetch_quote(stock["code"]), portfolio))
    results = []
    for stock, q in zip(portfolio, raw_quotes):
        q = dict(q)
        q["name"] = stock.get("name", stock["code"])
        q["shares"] = stock.get("shares", 0)
        q["cost"] = stock.get("cost", 0)
        q["status"] = stock.get("status", "holding" if stock.get("shares", 0) > 0 else "watch")
        if q["status"] == "holding" and q.get("price") and stock.get("shares") and stock.get("cost"):
            mv = q["price"] * stock["shares"]
            cv = stock["cost"] * stock["shares"]
            q["pnl"] = round(mv - cv, 0)
            q["pnl_pct"] = round((mv - cv) / cv * 100, 2) if cv else None
            q["market_value"] = round(mv, 0)
            q["cost_value"] = round(cv, 0)
        results.append(q)
    return results


def _sma(values, period):
    if len(values) < period:
        return None
    return sum(values[-period:]) / period


def _calc_screener_metrics(symbol, name):
    hist = fetch_history(symbol)
    if hist is None or getattr(hist, "empty", True) or len(hist) < 35:
        return None

    opens = [_safe_float(v) for v in hist["Open"].tolist() if v == v]
    highs = [_safe_float(v) for v in hist["High"].tolist() if v == v]
    lows = [_safe_float(v) for v in hist["Low"].tolist() if v == v]
    closes = [_safe_float(v) for v in hist["Close"].tolist() if v == v]
    volumes = [_safe_float(v) for v in hist["Volume"].tolist() if v == v]
    if min(len(opens), len(highs), len(lows), len(closes)) < 35 or len(volumes) < 6:
        return None

    last_close = closes[-1]
    prev_close = closes[-2] if len(closes) >= 2 else None
    last_volume = volumes[-1]
    avg_volume_5 = sum(volumes[-5:]) / 5 if len(volumes) >= 5 else None
    avg_volume_20 = sum(volumes[-20:]) / 20 if len(volumes) >= 20 else None
    ma5 = _sma(closes, 5)
    ma20 = _sma(closes, 20)
    ma60 = _sma(closes, 60)
    prev_ma5 = _sma(closes[:-1], 5) if len(closes) >= 6 else None
    prev_ma20 = _sma(closes[:-1], 20) if len(closes) >= 21 else None
    rsi14 = _calc_rsi(closes, 14)
    macd = _calc_macd(closes)
    change_pct = ((last_close - prev_close) / prev_close * 100) if prev_close else None
    volume_ratio = (last_volume / avg_volume_5) if avg_volume_5 else None
    high_20 = max(closes[-20:]) if len(closes) >= 20 else None
    breakout_20 = high_20 is not None and last_close >= high_20
    ma_cross = (
        prev_ma5 is not None and prev_ma20 is not None and ma5 is not None and ma20 is not None
        and prev_ma5 <= prev_ma20 and ma5 > ma20
    )
    above_ma20 = ma20 is not None and last_close > ma20

    return {
        "symbol": symbol,
        "name": resolve_stock_name(symbol, name),
        "price": round(last_close, 2),
        "change_pct": round(change_pct, 2) if change_pct is not None else None,
        "rsi14": round(rsi14, 2) if rsi14 is not None else None,
        "macd": round(macd.get("macd"), 4) if macd.get("macd") is not None else None,
        "macd_signal": round(macd.get("signal"), 4) if macd.get("signal") is not None else None,
        "macd_hist": round(macd.get("hist"), 4) if macd.get("hist") is not None else None,
        "macd_bull_cross": bool(macd.get("bull_cross")),
        "volume_ratio": round(volume_ratio, 2) if volume_ratio is not None else None,
        "avg_volume_20": int(avg_volume_20) if avg_volume_20 else None,
        "ma5": round(ma5, 2) if ma5 is not None else None,
        "ma20": round(ma20, 2) if ma20 is not None else None,
        "ma60": round(ma60, 2) if ma60 is not None else None,
        "ma_cross": ma_cross,
        "above_ma20": above_ma20,
        "breakout_20": breakout_20,
        "opens_20": [round(v, 2) for v in opens[-20:]],
        "highs_20": [round(v, 2) for v in highs[-20:]],
        "lows_20": [round(v, 2) for v in lows[-20:]],
        "closes_20": [round(v, 2) for v in closes[-20:]],
        "volumes_20": [int(v) for v in volumes[-20:]],
        "macd_series": [round(v, 4) for v in macd.get("macd_series", [])],
        "signal_series": [round(v, 4) for v in macd.get("signal_series", [])],
        "hist_series": [round(v, 4) for v in macd.get("hist_series", [])],
    }


def _match_screener_mode(metrics, mode):
    signals = []
    score = 0
    mode = (mode or "combo").lower()

    rsi14 = metrics.get("rsi14")
    change_pct = metrics.get("change_pct")
    volume_ratio = metrics.get("volume_ratio") or 0

    if rsi14 is not None and rsi14 < 30:
        signals.append(f"RSI {rsi14}")
        score += 18
    if metrics.get("macd_bull_cross"):
        signals.append("MACD 金叉")
        score += 16
    if volume_ratio >= 1.8 and (change_pct or 0) > 0:
        signals.append(f"放量 {volume_ratio}x")
        score += 14
    if metrics.get("ma_cross"):
        signals.append("MA5 上穿 MA20")
        score += 12
    if metrics.get("breakout_20"):
        signals.append("突破 20 日高点")
        score += 10
    if metrics.get("above_ma20"):
        signals.append("站上 MA20")
        score += 6

    if mode == "oversold":
        matched = rsi14 is not None and rsi14 < 30 and (change_pct is None or change_pct > -4)
    elif mode == "macd_cross":
        matched = metrics.get("macd_bull_cross") and volume_ratio >= 1.0
    elif mode == "volume_breakout":
        matched = volume_ratio >= 1.8 and (change_pct or 0) > 1.5
    elif mode == "ma_breakout":
        matched = metrics.get("ma_cross") or metrics.get("breakout_20")
    else:
        matched = score >= 18 and len(signals) >= 2

    return matched, score, signals[:3]


def get_screener_universe(universe_key):
    key = (universe_key or "core45").strip().lower()
    return key, SCREENER_UNIVERSE_MAP.get(key, SCREENER_UNIVERSE_MAP["core45"])


def run_screener(mode="combo", limit=15, universe_key="core45"):
    results = []
    processed = 0
    universe_key, universe = get_screener_universe(universe_key)
    universe_items = universe["items"]
    watchlist_codes = {item.get("code") for item in load_portfolio()}
    for metrics in SCREENER_EXECUTOR.map(
        lambda stock: _calc_screener_metrics(stock["code"], stock.get("name")),
        universe_items,
    ):
        if not metrics:
            continue
        processed += 1
        matched, score, signals = _match_screener_mode(metrics, mode)
        if not matched:
            continue
        metrics["score"] = score
        metrics["signals"] = signals
        metrics["in_watchlist"] = metrics["symbol"] in watchlist_codes
        results.append(metrics)

    results.sort(
        key=lambda item: (
            item.get("score", 0),
            item.get("volume_ratio") or 0,
            item.get("change_pct") or -999,
        ),
        reverse=True,
    )
    return {
        "mode": mode,
        "universe_key": universe_key,
        "universe": universe["label"],
        "universe_size": len(universe_items),
        "universe_description": universe.get("description", ""),
        "updated": datetime.now().strftime("%H:%M"),
        "count": len(results),
        "warning": "当前环境无法获取历史行情，请在可联网环境下刷新。" if processed == 0 else "",
        "items": results[:limit],
    }


@app.route("/")
def index():
    return render_template("index.html", current_page="dashboard")


@app.route("/screener")
def screener_page():
    return render_template("screener.html", current_page="screener")


@app.route("/chart")
def chart_page():
    symbol = request.args.get("symbol", "").strip().upper()
    name = request.args.get("name", "").strip()
    return render_template("chart.html", current_page="chart", symbol=symbol, name=name)


@app.route("/chart-smoke")
def chart_smoke_page():
    return render_template("chart_smoke.html", current_page="chart_smoke")


@app.route("/api/chart_history")
def chart_history_api():
    symbol = request.args.get("symbol", "").strip().upper()
    interval = request.args.get("interval", "D").strip().upper() or "D"
    if not symbol:
        return jsonify({"ok": False, "error": "missing symbol"}), 400
    return jsonify(build_chart_history(symbol, interval=interval))


@app.route("/api/quotes")
def quotes():
    return jsonify(get_portfolio_quotes())


@app.route("/api/portfolio", methods=["GET"])
def get_portfolio():
    return jsonify(load_portfolio())


@app.route("/api/portfolio", methods=["POST"])
def update_portfolio():
    save_portfolio(request.json)
    return jsonify({"ok": True})


@app.route("/api/add_stock", methods=["POST"])
def add_stock():
    body = request.json
    code = body.get("code", "").upper()
    if not code.endswith(".T"):
        code += ".T"
    portfolio = load_portfolio()
    if any(s["code"] == code for s in portfolio):
        return jsonify({"ok": False, "error": "already exists"})
    q = fetch_quote(code)
    if "error" in q and q.get("price") is None:
        return jsonify({"ok": False, "error": "not found"})
    portfolio.append({
        "code": code,
        "name": resolve_stock_name(code, body.get("name")),
        "shares": 0,
        "cost": 0,
        "status": "watch",
    })
    save_portfolio(portfolio)
    return jsonify({"ok": True})


@app.route("/api/remove_stock", methods=["POST"])
def remove_stock():
    code = request.json.get("code")
    portfolio = [s for s in load_portfolio() if s["code"] != code]
    save_portfolio(portfolio)
    return jsonify({"ok": True})


@app.route("/api/index_quotes")
def index_quotes():
    return jsonify(get_index_quotes_data())


@app.route("/api/dashboard_snapshot")
def dashboard_snapshot():
    return jsonify({
        "quotes": get_portfolio_quotes(),
        "indexes": get_index_quotes_data(),
        "updated": datetime.now().strftime("%H:%M:%S"),
    })


@app.route("/api/stock_news")
def stock_news():
    symbol = request.args.get("symbol", "").strip()
    if not symbol:
        return jsonify([])
    return jsonify(fetch_stock_news(symbol))


@app.route("/api/trump_news")
def trump_news():
    return jsonify(fetch_trump_news())


@app.route("/api/topic_digest")
def topic_digest():
    topic = request.args.get("topic", "").strip().lower()
    if not topic:
        return jsonify({"error": "missing topic"}), 400
    data = fetch_topic_digest(topic)
    if "error" in data:
        return jsonify(data), 404
    return jsonify(data)


@app.route("/api/screener")
def screener_api():
    mode = request.args.get("mode", "combo").strip().lower() or "combo"
    universe = request.args.get("universe", "core45").strip().lower() or "core45"
    try:
        limit = max(1, min(int(request.args.get("limit", "15")), 50))
    except Exception:
        limit = 15
    return jsonify(run_screener(mode=mode, limit=limit, universe_key=universe))


@app.errorhandler(404)
def handle_not_found(err):
    if request.path.startswith("/api/"):
        return jsonify({"ok": False, "error": f"API not found: {request.path}"}), 404
    return err


@app.errorhandler(Exception)
def handle_api_exception(err):
    if request.path.startswith("/api/"):
        print(f"[api_error] {request.path}: {err}")
        return jsonify({"ok": False, "error": str(err)}), 500
    raise err


if __name__ == "__main__":
    host = os.environ.get("HOST", "0.0.0.0").strip() or "0.0.0.0"
    port = env_int("PORT", 5555)
    debug_mode = env_bool("FLASK_DEBUG", True)
    print("\n" + "="*50)
    print("  日本株ポートフォリオ看板")
    print("="*50)
    print(f"  ブラウザで開く: http://localhost:{port}")
    print(f"  开发热加载: {'ON' if debug_mode else 'OFF'}")
    print("  停止: Ctrl+C")
    print("="*50 + "\n")
    app.run(host=host, port=port, debug=debug_mode, use_reloader=debug_mode)
