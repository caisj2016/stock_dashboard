#!/usr/bin/env python3
"""
日本株ポートフォリオ看板 - ローカルサーバー
使い方: python3 server.py
ブラウザで http://localhost:5555 を開く
"""

import json
import os
import re
import socket
import shutil
import threading
import time
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeoutError
from datetime import datetime, timedelta, timezone
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


def _parse_windows_proxy_server(value):
    raw = str(value or "").strip()
    if not raw:
        return {}
    if "=" not in raw:
        proxy = raw if "://" in raw else f"http://{raw}"
        return {"http": proxy, "https": proxy}

    proxies = {}
    for part in raw.split(";"):
        if "=" not in part:
            continue
        scheme, target = part.split("=", 1)
        scheme = scheme.strip().lower()
        target = target.strip()
        if not scheme or not target:
            continue
        proxies[scheme] = target if "://" in target else f"http://{target}"
    return proxies


def configure_system_proxy():
    existing = any(os.environ.get(key) for key in ["HTTP_PROXY", "HTTPS_PROXY", "http_proxy", "https_proxy"])
    if existing or os.name != "nt":
        return
    try:
        import winreg

        path = r"Software\Microsoft\Windows\CurrentVersion\Internet Settings"
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, path) as key:
            proxy_enable = winreg.QueryValueEx(key, "ProxyEnable")[0] if True else 0
            proxy_server = winreg.QueryValueEx(key, "ProxyServer")[0] if True else ""
    except Exception:
        return

    if not proxy_enable or not proxy_server:
        return

    proxies = _parse_windows_proxy_server(proxy_server)
    http_proxy = proxies.get("http") or proxies.get("https")
    https_proxy = proxies.get("https") or proxies.get("http")
    if http_proxy:
        os.environ.setdefault("HTTP_PROXY", http_proxy)
        os.environ.setdefault("http_proxy", http_proxy)
    if https_proxy:
        os.environ.setdefault("HTTPS_PROXY", https_proxy)
        os.environ.setdefault("https_proxy", https_proxy)


load_env_file(ENV_FILE)
configure_system_proxy()

JST = timezone(timedelta(hours=9))

app = Flask(__name__, static_folder="static", template_folder="templates")
CORS(app)
app.config["TEMPLATES_AUTO_RELOAD"] = env_bool("TEMPLATES_AUTO_RELOAD", True)
app.config["SEND_FILE_MAX_AGE_DEFAULT"] = env_int("SEND_FILE_MAX_AGE_DEFAULT", 0)

DATA_FILE = os.path.join(BASE_DIR, "portfolio.json")
DATA_BACKUP_DIR = os.path.join(BASE_DIR, "data_backups")
YF_CACHE_DIR = os.path.join(BASE_DIR, ".yf_cache")
os.makedirs(YF_CACHE_DIR, exist_ok=True)
os.makedirs(DATA_BACKUP_DIR, exist_ok=True)
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
PORTFOLIO_BACKUP_LIMIT = max(3, env_int("PORTFOLIO_BACKUP_LIMIT", 20))
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

STOCK_NAME_MAP.update({
    "1332.T": "ニッスイ",
    "1605.T": "ＩＮＰＥＸ",
    "1721.T": "コムシスＨＤ",
    "1801.T": "大成建",
    "1802.T": "大林組",
    "1803.T": "清水建",
    "1808.T": "長谷工",
    "1812.T": "鹿島",
    "1925.T": "ハウス",
    "1928.T": "積ハウス",
    "1963.T": "日揮ＨＤ",
    "2002.T": "日清粉Ｇ",
    "2269.T": "明治ＨＤ",
    "2282.T": "日ハム",
    "2413.T": "エムスリー",
    "2432.T": "ディーエヌエ",
    "2501.T": "サッポロＨＤ",
    "2502.T": "アサヒ",
    "2503.T": "キリンＨＤ",
    "2768.T": "双日",
    "2801.T": "キッコマン",
    "2802.T": "味の素",
    "285A.T": "キオクシア",
    "2871.T": "ニチレイ",
    "2914.T": "ＪＴ",
    "3086.T": "Ｊフロント",
    "3092.T": "ＺＯＺＯ",
    "3099.T": "三越伊勢丹",
    "3289.T": "東急不ＨＤ",
    "3382.T": "セブン＆アイ",
    "3401.T": "帝人",
    "3402.T": "東レ",
    "3405.T": "クラレ",
    "3407.T": "旭化成",
    "3436.T": "ＳＵＭＣＯ",
    "3659.T": "ネクソン",
    "3697.T": "ＳＨＩＦＴ",
    "3861.T": "王子ＨＤ",
    "4004.T": "レゾナック",
    "4005.T": "住友化",
    "4021.T": "日産化",
    "4042.T": "東ソー",
    "4043.T": "トクヤマ",
    "4061.T": "デンカ",
    "4062.T": "イビデン",
    "4063.T": "信越化",
    "4151.T": "協和キリン",
    "4183.T": "三井化学",
    "4188.T": "三菱ケミＧ",
    "4208.T": "ＵＢＥ",
    "4307.T": "野村総研",
    "4324.T": "電通グループ",
    "4385.T": "メルカリ",
    "4452.T": "花王",
    "4502.T": "武田",
    "4503.T": "アステラス",
    "4506.T": "住友ファーマ",
    "4507.T": "塩野義",
    "4519.T": "中外薬",
    "4523.T": "エーザイ",
    "4543.T": "テルモ",
    "4568.T": "第一三共",
    "4578.T": "大塚ＨＤ",
    "4661.T": "ＯＬＣ",
    "4689.T": "ラインヤフー",
    "4704.T": "トレンド",
    "4751.T": "サイバー",
    "4755.T": "楽天グループ",
    "4901.T": "富士フイルム",
    "4902.T": "コニカミノル",
    "4911.T": "資生堂",
    "5019.T": "出光興産",
    "5020.T": "ＥＮＥＯＳ",
    "5101.T": "浜ゴム",
    "5108.T": "ブリヂストン",
    "5201.T": "ＡＧＣ",
    "5214.T": "日電硝",
    "5233.T": "太平洋セメ",
    "5301.T": "東海カーボン",
    "5332.T": "ＴＯＴＯ",
    "5333.T": "ＮＧＫ",
    "5401.T": "日本製鉄",
    "5406.T": "神戸鋼",
    "5411.T": "ＪＦＥ",
    "543A.T": "ＡＲＣＨＩＯ",
    "5631.T": "日製鋼",
    "5706.T": "三井金属",
    "5711.T": "三菱マ",
    "5713.T": "住友鉱",
    "5714.T": "ＤＯＷＡ",
    "5801.T": "古河電",
    "5802.T": "住友電",
    "5803.T": "フジクラ",
    "5831.T": "しずおかＦＧ",
    "6098.T": "リクルート",
    "6103.T": "オークマ",
    "6113.T": "アマダ",
    "6146.T": "ディスコ",
    "6178.T": "日本郵政",
    "6273.T": "ＳＭＣ",
    "6301.T": "コマツ",
    "6302.T": "住友重",
    "6305.T": "日立建機",
    "6326.T": "クボタ",
    "6361.T": "荏原",
    "6367.T": "ダイキン",
    "6471.T": "日精工",
    "6472.T": "ＮＴＮ",
    "6473.T": "ジェイテクト",
    "6479.T": "ミネベア",
    "6501.T": "日立",
    "6503.T": "三菱電",
    "6504.T": "富士電機",
    "6506.T": "安川電",
    "6526.T": "ソシオネクス",
    "6532.T": "ベイカレント",
    "6645.T": "オムロン",
    "6701.T": "ＮＥＣ",
    "6702.T": "富士通",
    "6723.T": "ルネサス",
    "6724.T": "エプソン",
    "6752.T": "パナＨＤ",
    "6753.T": "シャープ",
    "6758.T": "ソニーＧ",
    "6762.T": "ＴＤＫ",
    "6770.T": "アルプスアル",
    "6841.T": "横河電",
    "6857.T": "アドテスト",
    "6861.T": "キーエンス",
    "6902.T": "デンソー",
    "6920.T": "レーザーテク",
    "6954.T": "ファナック",
    "6963.T": "ローム",
    "6971.T": "京セラ",
    "6976.T": "太陽誘電",
    "6981.T": "村田製",
    "6988.T": "日東電",
    "7004.T": "カナデビア",
    "7011.T": "三菱重",
    "7012.T": "川重",
    "7013.T": "ＩＨＩ",
    "7186.T": "横浜ＦＧ",
    "7201.T": "日産自",
    "7202.T": "いすゞ",
    "7203.T": "トヨタ",
    "7211.T": "三菱自",
    "7261.T": "マツダ",
    "7267.T": "ホンダ",
    "7269.T": "スズキ",
    "7270.T": "ＳＵＢＡＲＵ",
    "7272.T": "ヤマハ発",
    "7453.T": "良品計画",
    "7532.T": "パンパシＨＤ",
    "7731.T": "ニコン",
    "7733.T": "オリンパス",
    "7735.T": "スクリン",
    "7741.T": "ＨＯＹＡ",
    "7751.T": "キヤノン",
    "7752.T": "リコー",
    "7832.T": "バンナムＨＤ",
    "7911.T": "ＴＯＰＰＡＮ",
    "7912.T": "大日印",
    "7951.T": "ヤマハ",
    "7974.T": "任天堂",
    "8001.T": "伊藤忠",
    "8002.T": "丸紅",
    "8015.T": "豊田通商",
    "8031.T": "三井物",
    "8035.T": "東エレク",
    "8053.T": "住友商",
    "8058.T": "三菱商",
    "8233.T": "高島屋",
    "8252.T": "丸井Ｇ",
    "8253.T": "クレセゾン",
    "8267.T": "イオン",
    "8304.T": "あおぞら銀",
    "8306.T": "三菱ＵＦＪ",
    "8308.T": "りそなＨＤ",
    "8309.T": "三井住友トラ",
    "8316.T": "三井住友ＦＧ",
    "8331.T": "千葉銀",
    "8354.T": "ふくおかＦＧ",
    "8411.T": "みずほＦＧ",
    "8591.T": "オリックス",
    "8601.T": "大和",
    "8604.T": "野村",
    "8630.T": "ＳＯＭＰＯ",
    "8697.T": "日本取引所",
    "8725.T": "ＭＳ＆ＡＤ",
    "8750.T": "第一ライフ",
    "8766.T": "東京海上",
    "8795.T": "Ｔ＆Ｄ",
    "8801.T": "三井不",
    "8802.T": "菱地所",
    "8804.T": "東建物",
    "8830.T": "住友不",
    "9001.T": "東武",
    "9005.T": "東急",
    "9007.T": "小田急",
    "9008.T": "京王",
    "9009.T": "京成",
    "9020.T": "ＪＲ東日本",
    "9021.T": "ＪＲ西日本",
    "9022.T": "ＪＲ東海",
    "9064.T": "ヤマトＨＤ",
    "9101.T": "郵船",
    "9104.T": "商船三井",
    "9107.T": "川崎汽",
    "9147.T": "ＮＸＨＤ",
    "9201.T": "ＪＡＬ",
    "9202.T": "ＡＮＡＨＤ",
    "9432.T": "ＮＴＴ",
    "9433.T": "ＫＤＤＩ",
    "9434.T": "ＳＢ",
    "9501.T": "東電ＨＤ",
    "9502.T": "中部電",
    "9503.T": "関西電",
    "9531.T": "東ガス",
    "9532.T": "大ガス",
    "9602.T": "東宝",
    "9735.T": "セコム",
    "9766.T": "コナミＧ",
    "9843.T": "ニトリＨＤ",
    "9983.T": "ファストリ",
    "9984.T": "ＳＢＧ",
})

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

def _normalize_portfolio_items(data):
    normalized = []
    for item in data or []:
        code = str(item.get("code", "")).upper().strip()
        if not code:
            continue
        if not code.endswith(".T"):
            code += ".T"
        shares = int(float(item.get("shares", 0) or 0))
        cost = float(item.get("cost", 0) or 0)
        status = item.get("status") or ("holding" if shares > 0 else "watch")
        if status not in {"holding", "watch"}:
            status = "holding" if shares > 0 else "watch"
        normalized.append({
            "code": code,
            "name": resolve_stock_name(code, item.get("name")),
            "shares": shares if status == "holding" else 0,
            "cost": cost if status == "holding" else 0,
            "status": status,
        })
    return normalized or list(DEFAULT_PORTFOLIO)


def _backup_existing_portfolio():
    if not os.path.exists(DATA_FILE):
        return
    stamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_file = os.path.join(DATA_BACKUP_DIR, f"portfolio_{stamp}.json")
    shutil.copy2(DATA_FILE, backup_file)


def _prune_portfolio_backups():
    backups = []
    for name in os.listdir(DATA_BACKUP_DIR):
        if name.startswith("portfolio_") and name.endswith(".json"):
            full_path = os.path.join(DATA_BACKUP_DIR, name)
            if os.path.isfile(full_path):
                backups.append(full_path)
    backups.sort(key=os.path.getmtime, reverse=True)
    for old_file in backups[PORTFOLIO_BACKUP_LIMIT:]:
        try:
            os.remove(old_file)
        except OSError:
            pass


def _load_latest_portfolio_backup():
    backups = []
    for name in os.listdir(DATA_BACKUP_DIR):
        if name.startswith("portfolio_") and name.endswith(".json"):
            full_path = os.path.join(DATA_BACKUP_DIR, name)
            if os.path.isfile(full_path):
                backups.append(full_path)
    backups.sort(key=os.path.getmtime, reverse=True)
    for backup_file in backups:
        try:
            with open(backup_file, "r", encoding="utf-8") as f:
                data = json.load(f)
            if isinstance(data, list):
                return data
        except Exception:
            continue
    return None


def load_portfolio():
    if os.path.exists(DATA_FILE):
        try:
            with open(DATA_FILE, "r", encoding="utf-8") as f:
                data = json.load(f)
        except Exception:
            data = _load_latest_portfolio_backup()
        if isinstance(data, list):
            return _normalize_portfolio_items(data)
    return DEFAULT_PORTFOLIO


def save_portfolio(data):
    normalized = _normalize_portfolio_items(data)
    _backup_existing_portfolio()
    temp_file = DATA_FILE + ".tmp"
    with open(temp_file, "w", encoding="utf-8") as f:
        json.dump(normalized, f, ensure_ascii=False, indent=2)
        f.flush()
        os.fsync(f.fileno())
    os.replace(temp_file, DATA_FILE)
    _prune_portfolio_backups()


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


def _contains_cjk(text):
    return bool(re.search(r"[\u3040-\u30ff\u3400-\u9fff\uff00-\uffef]", str(text or "")))


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
    mapped_name = STOCK_NAME_MAP.get((code or "").upper())
    if mapped_name and not _is_symbol_like_name(mapped_name, code):
        return mapped_name
    if (
        current_name
        and str(current_name).strip()
        and not _is_symbol_like_name(current_name, code)
        and _contains_cjk(current_name)
    ):
        return str(current_name).strip()
    fetched_name = fetch_company_name(code)
    if fetched_name and not _is_symbol_like_name(fetched_name, code):
        return fetched_name
    if current_name and str(current_name).strip() and not _is_symbol_like_name(current_name, code):
        return str(current_name).strip()
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

TOPIC_RSS_FEEDS = {
    "nikkei": [
        "https://feeds.reuters.com/reuters/businessNews",
        "https://feeds.reuters.com/reuters/worldNews",
        "https://feeds.apnews.com/rss/apf-business",
        "http://feeds.bbci.co.uk/news/business/rss.xml",
        "https://www.cnbc.com/id/100003114/device/rss/rss.html",
    ],
    "semiconductor": [
        "https://feeds.reuters.com/reuters/technologyNews",
        "https://feeds.reuters.com/reuters/businessNews",
        "https://feeds.apnews.com/rss/apf-business",
        "http://feeds.bbci.co.uk/news/technology/rss.xml",
        "https://www.cnbc.com/id/19854910/device/rss/rss.html",
    ],
}

TOPIC_HTML_FEEDS = {
    "nikkei": [
        {"name": "Japan Times Business", "url": "https://www.japantimes.co.jp/business/"},
        {"name": "Japan Times Business News", "url": "https://www.japantimes.co.jp/news/news/business/"},
        {"name": "Japan Times Economy", "url": "https://www.japantimes.co.jp/business/economy/"},
        {"name": "Japan Times Economic", "url": "https://www.japantimes.co.jp/economy/economic/"},
        {"name": "Japan Times Business New", "url": "https://www.japantimes.co.jp/businessnew"},
    ],
    "semiconductor": [
        {"name": "Japan Times Business", "url": "https://www.japantimes.co.jp/business/"},
    ],
}

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


def _pub_datetime_jst(raw):
    try:
        if not raw:
            return None
        if str(raw).isdigit():
            dt = datetime.fromtimestamp(int(raw), tz=timezone.utc)
        else:
            dt = parsedate_to_datetime(raw)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
        return dt.astimezone(JST)
    except Exception:
        return None


def _is_today_jst(raw):
    dt = _pub_datetime_jst(raw)
    if not dt:
        return False
    return dt.date() == datetime.now(JST).date()


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
TOPIC_DIGEST_RECENT_HOURS = 72
TOPIC_DIGEST_MAX_AGE_DAYS = 7
TOPIC_DIGEST_OLDER_MAX_DAYS = 30
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

TOPIC_CONFIG["nikkei"].update({
    "topic_terms": [
        "nikkei", "nikkei 225", "japan stocks", "japanese stocks", "tokyo stocks",
        "tokyo stock exchange", "boj", "bank of japan", "usd/jpy", "yen", "japan market",
    ],
    "fallback_driver": "最新市场动态",
})

TOPIC_CONFIG["semiconductor"].update({
    "topic_terms": [
        "semiconductor", "semiconductors", "chip", "chips", "foundry", "wafer",
        "hbm", "memory", "dram", "nand", "gpu", "ai server", "datacenter",
        "tsmc", "nvidia", "asml", "tokyo electron", "screen", "sumco",
        "renesas", "advantest", "lasertec",
    ],
    "fallback_driver": "最新产业动态",
})

TOPIC_CONFIG["nikkei"].update({
    "queries": [
        "Nikkei 225 OR TOPIX OR Japan stocks OR Tokyo stocks earnings OR outlook OR guidance Reuters Bloomberg Nikkei Asia CNBC",
        "BOJ OR Bank of Japan OR USDJPY OR yen Japan market yield Reuters Bloomberg CNBC",
        "Japan exporters OR Toyota OR Sony OR Nintendo OR Hitachi OR SoftBank OR Fast Retailing OR Tokyo Electron Japan stocks demand OR tariff Reuters Bloomberg CNBC",
        "Mitsubishi UFJ OR Sumitomo Mitsui OR Mitsubishi Corp OR Itochu OR Marubeni OR Tokyo Electron Japan market Reuters Bloomberg CNBC",
    ],
    "drivers": {
        "日元汇率": ["usd/jpy", "usdjpy", "yen", "jpy", "dollar-yen", "weaker yen", "stronger yen", "fx"],
        "日本央行": ["boj", "bank of japan", "ueda", "rate", "rates", "yield", "jgb", "policy meeting", "normalization"],
        "全球风险偏好": ["wall street", "nasdaq", "s&p 500", "futures", "risk-on", "risk off", "treasury yield", "vix"],
        "出口龙头": ["toyota", "sony", "nintendo", "hitachi", "export", "exporters", "machinery", "factory activity"],
        "财报指引": ["earnings", "guidance", "profit", "results", "forecast", "outlook", "operating profit", "revision"],
        "回购分红治理": ["buyback", "shareholder return", "dividend", "payout", "governance", "tse reform", "price-to-book", "activist"],
    },
    "positive": ["surge", "gain", "gains", "rise", "rally", "beat", "up", "higher", "weaker yen", "record", "buyback", "dividend hike", "upgrade"],
    "negative": ["fall", "falls", "drop", "drops", "slump", "miss", "down", "lower", "stronger yen", "selloff", "downgrade", "profit warning"],
    "topic_terms": [
        "nikkei", "nikkei 225", "japan stocks", "japanese stocks", "tokyo stocks",
        "tokyo stock exchange", "boj", "bank of japan", "usd/jpy", "yen", "japan market",
        "exporters", "buyback", "dividend", "governance", "topix", "tse reform",
        "earnings", "guidance", "outlook", "tariff", "machinery", "factory activity",
        "shareholder return", "price-to-book", "jgb", "yield",
        "japan inc", "asia stocks", "tokyo-listed", "japanese exporters", "japan earnings",
        "yen-sensitive", "tse prime", "softbank", "fast retailing", "tokyo electron",
        "mufg", "mitsubishi ufj", "sumitomo mitsui", "mitsubishi corp", "itochu", "marubeni",
    ],
    "core_topic_terms": [
        "nikkei", "nikkei 225", "topix", "japan stocks", "japanese stocks", "tokyo stocks",
        "tokyo stock exchange", "boj", "bank of japan", "usd/jpy", "yen", "japanese exporters",
    ],
    "anchor_terms": [
        "nikkei", "nikkei 225", "topix", "japan stocks", "japanese stocks", "tokyo stocks",
        "tokyo stock exchange", "boj", "bank of japan", "usd/jpy", "yen", "jgb", "exporters",
        "toyota", "sony", "nintendo", "hitachi", "softbank", "fast retailing", "tokyo electron",
        "mitsubishi ufj", "sumitomo mitsui", "mitsubishi corp", "itochu", "marubeni",
    ],
    "fallback_driver": "最新市场动态",
})

TOPIC_CONFIG["semiconductor"].update({
    "queries": [
        "semiconductor OR chip OR chips OR foundry OR TSMC OR Nvidia earnings OR guidance Reuters Bloomberg Nikkei Asia CNBC",
        "AI server OR GPU OR HBM OR memory OR datacenter Nvidia Micron SK hynix demand OR capex Reuters Bloomberg CNBC",
        "ASML OR Tokyo Electron OR Screen OR Advantest OR Lasertec OR Renesas OR SUMCO semiconductor restriction OR approval Reuters Bloomberg CNBC",
    ],
    "drivers": {
        "AI算力需求": ["ai", "nvidia", "gpu", "ai server", "datacenter", "blackwell", "grace blackwell", "inference", "training cluster"],
        "先进制程代工": ["tsmc", "2nm", "3nm", "5nm", "wafer", "foundry", "advanced packaging", "cowos", "packaging"],
        "设备资本开支": ["asml", "tokyo electron", "screen", "advantest", "lasertec", "capex", "equipment", "lithography", "wfe"],
        "HBM与存储": ["hbm", "dram", "nand", "memory", "sk hynix", "micron", "samsung", "ddr5", "bandwidth memory"],
        "日本材料零部件": ["sumco", "shin-etsu", "jsr", "resonac", "wafer", "photoresist", "silicon", "substrate"],
        "政策限制": ["china", "export control", "sanction", "restriction", "tariff", "entity list", "license requirement"],
    },
    "positive": ["surge", "gain", "gains", "rise", "rally", "beat", "record", "expand", "strong demand", "boost", "capacity expansion", "raised forecast"],
    "negative": ["fall", "falls", "drop", "drops", "slump", "cut", "delay", "weak demand", "restriction", "tariff", "inventory correction", "guidance cut"],
    "topic_terms": [
        "semiconductor", "semiconductors", "chip", "chips", "foundry", "wafer",
        "hbm", "memory", "dram", "nand", "gpu", "ai server", "datacenter",
        "tsmc", "nvidia", "asml", "tokyo electron", "screen", "sumco",
        "renesas", "advantest", "lasertec", "cowos", "advanced packaging", "photoresist",
        "equipment", "wfe", "hynix", "micron", "capex", "earnings", "guidance", "outlook",
        "approval", "restriction", "export control", "inventory", "pricing", "demand",
    ],
    "core_topic_terms": [
        "semiconductor", "semiconductors", "chip", "chips", "foundry", "wafer",
        "hbm", "memory", "gpu", "ai server", "datacenter", "tsmc", "nvidia",
        "asml", "tokyo electron", "screen", "advantest", "lasertec", "renesas", "sumco",
    ],
    "anchor_terms": [
        "semiconductor", "semiconductors", "chip", "chips", "foundry", "wafer",
        "hbm", "memory", "dram", "nand", "gpu", "ai server", "datacenter",
        "advanced packaging", "cowos", "equipment", "lithography", "wfe", "photoresist",
        "asml", "tokyo electron", "screen", "advantest", "lasertec", "tsmc", "micron", "sk hynix",
    ],
    "fallback_driver": "最新产业动态",
})

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

SCREENER_UNIVERSE_NIKKEI_225 = [
    {"code": "1332.T", "name": "NISSUI CORP."},
    {"code": "1605.T", "name": "INPEX CORP."},
    {"code": "1721.T", "name": "COMSYS HOLDINGS CORP."},
    {"code": "1801.T", "name": "TAISEI CORP."},
    {"code": "1802.T", "name": "OBAYASHI CORP."},
    {"code": "1803.T", "name": "SHIMIZU CORP."},
    {"code": "1808.T", "name": "HASEKO CORP."},
    {"code": "1812.T", "name": "KAJIMA CORP."},
    {"code": "1925.T", "name": "DAIWA HOUSE IND. CO., LTD."},
    {"code": "1928.T", "name": "SEKISUI HOUSE, LTD."},
    {"code": "1963.T", "name": "JGC HOLDINGS CORP."},
    {"code": "2002.T", "name": "NISSHIN SEIFUN GROUP INC."},
    {"code": "2269.T", "name": "MEIJI HOLDINGS CO., LTD."},
    {"code": "2282.T", "name": "NH FOODS LTD."},
    {"code": "2413.T", "name": "M3, INC."},
    {"code": "2432.T", "name": "DENA CO., LTD."},
    {"code": "2501.T", "name": "SAPPORO HOLDINGS LTD."},
    {"code": "2502.T", "name": "ASAHI GROUP HOLDINGS, LTD."},
    {"code": "2503.T", "name": "KIRIN HOLDINGS CO., LTD."},
    {"code": "2768.T", "name": "SOJITZ CORP."},
    {"code": "2801.T", "name": "KIKKOMAN CORP."},
    {"code": "2802.T", "name": "AJINOMOTO CO., INC."},
    {"code": "285A.T", "name": "KIOXIA HOLDINGS CORP."},
    {"code": "2871.T", "name": "NICHIREI CORP."},
    {"code": "2914.T", "name": "JAPAN TOBACCO INC."},
    {"code": "3086.T", "name": "J.FRONT RETAILING CO., LTD."},
    {"code": "3092.T", "name": "ZOZO, INC."},
    {"code": "3099.T", "name": "ISETAN MITSUKOSHI HOLDINGS LTD."},
    {"code": "3289.T", "name": "TOKYU FUDOSAN HOLDINGS CORP."},
    {"code": "3382.T", "name": "SEVEN & I HOLDINGS CO., LTD."},
    {"code": "3401.T", "name": "TEIJIN LTD."},
    {"code": "3402.T", "name": "TORAY INDUSTRIES, INC."},
    {"code": "3405.T", "name": "KURARAY CO., LTD."},
    {"code": "3407.T", "name": "ASAHI KASEI CORP."},
    {"code": "3436.T", "name": "SUMCO CORP."},
    {"code": "3659.T", "name": "NEXON CO., LTD."},
    {"code": "3697.T", "name": "SHIFT INC."},
    {"code": "3861.T", "name": "OJI HOLDINGS CORP."},
    {"code": "4004.T", "name": "RESONAC HOLDINGS CORP."},
    {"code": "4005.T", "name": "SUMITOMO CHEMICAL CO., LTD."},
    {"code": "4021.T", "name": "NISSAN CHEMICAL CORP."},
    {"code": "4042.T", "name": "TOSOH CORP."},
    {"code": "4043.T", "name": "TOKUYAMA CORP."},
    {"code": "4061.T", "name": "DENKA CO., LTD."},
    {"code": "4062.T", "name": "IBIDEN CO., LTD."},
    {"code": "4063.T", "name": "SHIN-ETSU CHEMICAL CO., LTD."},
    {"code": "4151.T", "name": "KYOWA KIRIN CO., LTD."},
    {"code": "4183.T", "name": "MITSUI CHEMICALS, INC."},
    {"code": "4188.T", "name": "MITSUBISHI CHEMICAL GROUP CORP."},
    {"code": "4208.T", "name": "UBE CORP."},
    {"code": "4307.T", "name": "NOMURA RESEARCH INSTITUTE, LTD."},
    {"code": "4324.T", "name": "DENTSU GROUP INC."},
    {"code": "4385.T", "name": "MERCARI, INC."},
    {"code": "4452.T", "name": "KAO CORP."},
    {"code": "4502.T", "name": "TAKEDA PHARMACEUTICAL CO., LTD."},
    {"code": "4503.T", "name": "ASTELLAS PHARMA INC."},
    {"code": "4506.T", "name": "SUMITOMO PHARMA CO., LTD."},
    {"code": "4507.T", "name": "SHIONOGI & CO., LTD."},
    {"code": "4519.T", "name": "CHUGAI PHARMACEUTICAL CO., LTD."},
    {"code": "4523.T", "name": "EISAI CO., LTD."},
    {"code": "4543.T", "name": "TERUMO CORP."},
    {"code": "4568.T", "name": "DAIICHI SANKYO CO., LTD."},
    {"code": "4578.T", "name": "OTSUKA HOLDINGS CO., LTD."},
    {"code": "4661.T", "name": "ORIENTAL LAND CO., LTD."},
    {"code": "4689.T", "name": "LY CORP."},
    {"code": "4704.T", "name": "TREND MICRO INC."},
    {"code": "4751.T", "name": "CYBERAGENT, INC."},
    {"code": "4755.T", "name": "RAKUTEN GROUP, INC."},
    {"code": "4901.T", "name": "FUJIFILM HOLDINGS CORP."},
    {"code": "4902.T", "name": "KONICA MINOLTA, INC."},
    {"code": "4911.T", "name": "SHISEIDO CO., LTD."},
    {"code": "5019.T", "name": "IDEMITSU KOSAN CO., LTD."},
    {"code": "5020.T", "name": "ENEOS HOLDINGS, INC."},
    {"code": "5101.T", "name": "THE YOKOHAMA RUBBER CO., LTD."},
    {"code": "5108.T", "name": "BRIDGESTONE CORP."},
    {"code": "5201.T", "name": "AGC INC."},
    {"code": "5214.T", "name": "NIPPON ELECTRIC GLASS CO., LTD."},
    {"code": "5233.T", "name": "TAIHEIYO CEMENT CORP."},
    {"code": "5301.T", "name": "TOKAI CARBON CO., LTD."},
    {"code": "5332.T", "name": "TOTO LTD."},
    {"code": "5333.T", "name": "NGK CORP."},
    {"code": "5401.T", "name": "NIPPON STEEL CORP."},
    {"code": "5406.T", "name": "KOBE STEEL, LTD."},
    {"code": "5411.T", "name": "JFE HOLDINGS, INC."},
    {"code": "543A.T", "name": "ARCHION CORP."},
    {"code": "5631.T", "name": "THE JAPAN STEEL WORKS, LTD."},
    {"code": "5706.T", "name": "MITSUI KINZOKU CO., LTD."},
    {"code": "5711.T", "name": "MITSUBISHI MATERIALS CORP."},
    {"code": "5713.T", "name": "SUMITOMO METAL MINING CO., LTD."},
    {"code": "5714.T", "name": "DOWA HOLDINGS CO., LTD."},
    {"code": "5801.T", "name": "FURUKAWA ELECTRIC CO., LTD."},
    {"code": "5802.T", "name": "SUMITOMO ELECTRIC IND., LTD."},
    {"code": "5803.T", "name": "FUJIKURA LTD."},
    {"code": "5831.T", "name": "SHIZUOKA FINANCIAL GROUP, INC."},
    {"code": "6098.T", "name": "RECRUIT HOLDINGS CO., LTD."},
    {"code": "6103.T", "name": "OKUMA CORP."},
    {"code": "6113.T", "name": "AMADA CO., LTD."},
    {"code": "6146.T", "name": "DISCO CORP."},
    {"code": "6178.T", "name": "JAPAN POST HOLDINGS CO., LTD."},
    {"code": "6273.T", "name": "SMC CORP."},
    {"code": "6301.T", "name": "KOMATSU LTD."},
    {"code": "6302.T", "name": "SUMITOMO HEAVY IND., LTD."},
    {"code": "6305.T", "name": "HITACHI CONST. MACH. CO., LTD."},
    {"code": "6326.T", "name": "KUBOTA CORP."},
    {"code": "6361.T", "name": "EBARA CORP."},
    {"code": "6367.T", "name": "DAIKIN INDUSTRIES, LTD."},
    {"code": "6471.T", "name": "NSK LTD."},
    {"code": "6472.T", "name": "NTN CORP."},
    {"code": "6473.T", "name": "JTEKT CORP."},
    {"code": "6479.T", "name": "MINEBEA MITSUMI INC."},
    {"code": "6501.T", "name": "HITACHI, LTD."},
    {"code": "6503.T", "name": "MITSUBISHI ELECTRIC CORP."},
    {"code": "6504.T", "name": "FUJI ELECTRIC CO., LTD."},
    {"code": "6506.T", "name": "YASKAWA ELECTRIC CORP."},
    {"code": "6526.T", "name": "SOCIONEXT INC."},
    {"code": "6532.T", "name": "BAYCURRENT, INC."},
    {"code": "6645.T", "name": "OMRON CORP."},
    {"code": "6701.T", "name": "NEC CORP."},
    {"code": "6702.T", "name": "FUJITSU LTD."},
    {"code": "6723.T", "name": "RENESAS ELECTRONICS CORP."},
    {"code": "6724.T", "name": "SEIKO EPSON CORP."},
    {"code": "6752.T", "name": "PANASONIC HOLDINGS CORP."},
    {"code": "6753.T", "name": "SHARP CORP."},
    {"code": "6758.T", "name": "SONY GROUP CORP."},
    {"code": "6762.T", "name": "TDK CORP."},
    {"code": "6770.T", "name": "ALPS ALPINE CO., LTD."},
    {"code": "6841.T", "name": "YOKOGAWA ELECTRIC CORP."},
    {"code": "6857.T", "name": "ADVANTEST CORP."},
    {"code": "6861.T", "name": "KEYENCE CORP."},
    {"code": "6902.T", "name": "DENSO CORP."},
    {"code": "6920.T", "name": "LASERTEC CORP."},
    {"code": "6954.T", "name": "FANUC CORP."},
    {"code": "6963.T", "name": "ROHM CO., LTD."},
    {"code": "6971.T", "name": "KYOCERA CORP."},
    {"code": "6976.T", "name": "TAIYO YUDEN CO., LTD."},
    {"code": "6981.T", "name": "MURATA MANUFACTURING CO., LTD."},
    {"code": "6988.T", "name": "NITTO DENKO CORP."},
    {"code": "7004.T", "name": "KANADEVIA CORP."},
    {"code": "7011.T", "name": "MITSUBISHI HEAVY IND., LTD."},
    {"code": "7012.T", "name": "KAWASAKI HEAVY IND., LTD."},
    {"code": "7013.T", "name": "IHI CORP."},
    {"code": "7186.T", "name": "YOKOHAMA FINANCIAL GROUP, INC."},
    {"code": "7201.T", "name": "NISSAN MOTOR CO., LTD."},
    {"code": "7202.T", "name": "ISUZU MOTORS LTD."},
    {"code": "7203.T", "name": "TOYOTA MOTOR CORP."},
    {"code": "7211.T", "name": "MITSUBISHI MOTORS CORP."},
    {"code": "7261.T", "name": "MAZDA MOTOR CORP."},
    {"code": "7267.T", "name": "HONDA MOTOR CO., LTD."},
    {"code": "7269.T", "name": "SUZUKI MOTOR CORP."},
    {"code": "7270.T", "name": "SUBARU CORP."},
    {"code": "7272.T", "name": "YAMAHA MOTOR CO., LTD."},
    {"code": "7453.T", "name": "RYOHIN KEIKAKU CO., LTD."},
    {"code": "7532.T", "name": "PAN PACIFIC INTERNATIONAL HOLDINGS CORP."},
    {"code": "7731.T", "name": "NIKON CORP."},
    {"code": "7733.T", "name": "OLYMPUS CORP."},
    {"code": "7735.T", "name": "SCREEN HOLDINGS CO., LTD."},
    {"code": "7741.T", "name": "HOYA CORP."},
    {"code": "7751.T", "name": "CANON INC."},
    {"code": "7752.T", "name": "RICOH CO., LTD."},
    {"code": "7832.T", "name": "BANDAI NAMCO HOLDINGS INC."},
    {"code": "7911.T", "name": "TOPPAN HOLDINGS INC."},
    {"code": "7912.T", "name": "DAI NIPPON PRINTING CO., LTD."},
    {"code": "7951.T", "name": "YAMAHA CORP."},
    {"code": "7974.T", "name": "NINTENDO CO., LTD."},
    {"code": "8001.T", "name": "ITOCHU CORP."},
    {"code": "8002.T", "name": "MARUBENI CORP."},
    {"code": "8015.T", "name": "TOYOTA TSUSHO CORP."},
    {"code": "8031.T", "name": "MITSUI & CO., LTD."},
    {"code": "8035.T", "name": "TOKYO ELECTRON LTD."},
    {"code": "8053.T", "name": "SUMITOMO CORP."},
    {"code": "8058.T", "name": "MITSUBISHI CORP."},
    {"code": "8233.T", "name": "TAKASHIMAYA CO., LTD."},
    {"code": "8252.T", "name": "MARUI GROUP CO., LTD."},
    {"code": "8253.T", "name": "CREDIT SAISON CO., LTD."},
    {"code": "8267.T", "name": "AEON CO., LTD."},
    {"code": "8304.T", "name": "AOZORA BANK, LTD."},
    {"code": "8306.T", "name": "MITSUBISHI UFJ FINANCIAL GROUP, INC."},
    {"code": "8308.T", "name": "RESONA HOLDINGS, INC."},
    {"code": "8309.T", "name": "SUMITOMO MITSUI TRUST GROUP, INC."},
    {"code": "8316.T", "name": "SUMITOMO MITSUI FINANCIAL GROUP, INC."},
    {"code": "8331.T", "name": "THE CHIBA BANK, LTD."},
    {"code": "8354.T", "name": "FUKUOKA FINANCIAL GROUP, INC."},
    {"code": "8411.T", "name": "MIZUHO FINANCIAL GROUP, INC."},
    {"code": "8591.T", "name": "ORIX CORP."},
    {"code": "8601.T", "name": "DAIWA SECURITIES GROUP INC."},
    {"code": "8604.T", "name": "NOMURA HOLDINGS, INC."},
    {"code": "8630.T", "name": "SOMPO HOLDINGS, INC."},
    {"code": "8697.T", "name": "JAPAN EXCHANGE GROUP, INC."},
    {"code": "8725.T", "name": "MS&AD INSURANCE GROUP HOLDINGS, INC."},
    {"code": "8750.T", "name": "DAIICHI LIFE GROUP, INC."},
    {"code": "8766.T", "name": "TOKIO MARINE HOLDINGS, INC."},
    {"code": "8795.T", "name": "T&D HOLDINGS, INC."},
    {"code": "8801.T", "name": "MITSUI FUDOSAN CO., LTD."},
    {"code": "8802.T", "name": "MITSUBISHI ESTATE CO., LTD."},
    {"code": "8804.T", "name": "TOKYO TATEMONO CO., LTD."},
    {"code": "8830.T", "name": "SUMITOMO REALTY & DEVELOPMENT CO., LTD."},
    {"code": "9001.T", "name": "TOBU RAILWAY CO., LTD."},
    {"code": "9005.T", "name": "TOKYU CORP."},
    {"code": "9007.T", "name": "ODAKYU ELECTRIC RAILWAY CO., LTD."},
    {"code": "9008.T", "name": "KEIO CORP."},
    {"code": "9009.T", "name": "KEISEI ELECTRIC RAILWAY CO., LTD."},
    {"code": "9020.T", "name": "EAST JAPAN RAILWAY CO."},
    {"code": "9021.T", "name": "WEST JAPAN RAILWAY CO."},
    {"code": "9022.T", "name": "CENTRAL JAPAN RAILWAY CO., LTD."},
    {"code": "9064.T", "name": "YAMATO HOLDINGS CO., LTD."},
    {"code": "9101.T", "name": "NIPPON YUSEN K.K."},
    {"code": "9104.T", "name": "MITSUI O.S.K.LINES, LTD."},
    {"code": "9107.T", "name": "KAWASAKI KISEN KAISHA, LTD."},
    {"code": "9147.T", "name": "NIPPON EXPRESS HOLDINGS, INC."},
    {"code": "9201.T", "name": "JAPAN AIRLINES CO., LTD."},
    {"code": "9202.T", "name": "ANA HOLDINGS INC."},
    {"code": "9432.T", "name": "NTT, INC."},
    {"code": "9433.T", "name": "KDDI CORP."},
    {"code": "9434.T", "name": "SOFTBANK CORP."},
    {"code": "9501.T", "name": "TOKYO ELECTRIC POWER COMPANY HOLDINGS, INC."},
    {"code": "9502.T", "name": "CHUBU ELECTRIC POWER CO., INC."},
    {"code": "9503.T", "name": "THE KANSAI ELECTRIC POWER CO., INC."},
    {"code": "9531.T", "name": "TOKYO GAS CO., LTD."},
    {"code": "9532.T", "name": "OSAKA GAS CO., LTD."},
    {"code": "9602.T", "name": "TOHO CO., LTD."},
    {"code": "9735.T", "name": "SECOM CO., LTD."},
    {"code": "9766.T", "name": "KONAMI GROUP CORP."},
    {"code": "9843.T", "name": "NITORI HOLDINGS CO., LTD."},
    {"code": "9983.T", "name": "FAST RETAILING CO., LTD."},
    {"code": "9984.T", "name": "SOFTBANK GROUP CORP."},
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

SCREENER_UNIVERSE_MAP["nikkei225"] = {
    "label": "Nikkei 225",
    "description": "Nikkei 225 成分股全量样本",
    "items": SCREENER_UNIVERSE_NIKKEI_225,
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


def _strip_feed_source_suffix(text, source):
    cleaned = _normalize_whitespace(text)
    src = _normalize_whitespace(source)
    if not cleaned or not src:
        return cleaned
    pattern = re.compile(r"(?:\s+|[\-|:|•])" + re.escape(src) + r"$", re.IGNORECASE)
    return pattern.sub("", cleaned).strip()


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
            desc = _strip_feed_source_suffix(desc, source)
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


def _parse_html_topic_page(url, source_name, limit=12):
    try:
        r = requests.get(url, timeout=10, headers=HEADERS)
        r.raise_for_status()
        html = r.text or ""
        items = []
        seen = set()
        pattern = re.compile(r'<a[^>]+href="([^"]+)"[^>]*>(.*?)</a>', re.IGNORECASE | re.DOTALL)
        for href, inner in pattern.findall(html):
            title = re.sub(r"<[^>]+>", " ", inner)
            title = unescape(_normalize_whitespace(title))
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
            pub_raw = _guess_pubdate_from_url(href)
            summary = _extract_html_card_summary(html, href, fallback_title=title)
            items.append({
                "title": title,
                "summary": summary,
                "pub_raw": pub_raw,
                "sort_ts": _pub_ts(pub_raw) if pub_raw else 0,
                "pub": _fmt_pubdate(pub_raw) if pub_raw else source_name,
                "url": href,
                "provider": source_name,
                "source": source_name,
            })
            if len(items) >= limit:
                break
        return items
    except Exception as e:
        print(f"[topic_html] {url} error: {e}")
        return []


def _guess_pubdate_from_url(url):
    m = re.search(r"/(20\d{2})/(\d{2})/(\d{2})/", str(url or ""))
    if not m:
        return ""
    year, month, day = m.groups()
    return f"{year}-{month}-{day} 00:00:00 +0900"


def _extract_html_card_summary(html, href, fallback_title=""):
    if not html or not href:
        return ""
    idx = html.find(href)
    if idx < 0:
        return ""
    window = html[idx: idx + 1400]
    text = re.sub(r"<[^>]+>", " ", window)
    text = unescape(_normalize_whitespace(text))
    if fallback_title:
        text = text.replace(fallback_title, "").strip()
    parts = re.split(r"\s{2,}|(?<=[.!?])\s+", text)
    for part in parts:
        cleaned = _normalize_whitespace(part)
        if 30 <= len(cleaned) <= 220 and href not in cleaned:
            return cleaned[:220]
    return ""


def _normalize_topic_feed_item(item):
    normalized = dict(item)
    normalized["provider"] = item.get("provider") or item.get("source") or _rss_source(item.get("url", ""))
    normalized["summary"] = item.get("summary") or item.get("summary_zh") or ""
    normalized["title"] = item.get("title") or item.get("title_zh") or ""
    normalized["pub_raw"] = item.get("pub_raw") or item.get("pub") or ""
    normalized["sort_ts"] = item.get("sort_ts") or _pub_ts(normalized["pub_raw"])
    normalized["pub"] = item.get("pub") or (_fmt_pubdate(normalized["pub_raw"]) if normalized["pub_raw"] else "")
    return normalized


def _topic_recall_score(item, topic_cfg):
    title = (item.get("title") or "").lower()
    body = " ".join([item.get("title", ""), item.get("summary", "")]).lower()
    score = 0
    core_hits = 0
    anchor_hits = 0
    for keyword in topic_cfg.get("core_topic_terms", []):
        if _contains_term(title, keyword):
            core_hits += 2
        elif _contains_term(body, keyword):
            core_hits += 1
    for keyword in topic_cfg.get("anchor_terms", []):
        if _contains_term(title, keyword):
            anchor_hits += 2
        elif _contains_term(body, keyword):
            anchor_hits += 1
    for keyword in topic_cfg.get("topic_terms", []):
        if _contains_term(title, keyword):
            score += 3
        elif _contains_term(body, keyword):
            score += 1
    for keywords in topic_cfg.get("drivers", {}).values():
        if any(_contains_term(body, keyword) for keyword in keywords):
            score += 4
    return score, core_hits, anchor_hits


def _fetch_topic_candidate_items(topic_key, topic_cfg, limit=24):
    candidates = []
    queries = topic_cfg.get("queries") or [topic_cfg.get("query", "")]
    for query in queries:
        google_items = _parse_google_news_rss(query, limit=max(8, limit // max(1, len(queries))))
        candidates.extend(_normalize_topic_feed_item(item) for item in google_items)

    for feed in TOPIC_RSS_FEEDS.get(topic_key, []):
        for item in _parse_rss(feed):
            normalized = _normalize_topic_feed_item(item)
            recall_score, core_hits, anchor_hits = _topic_recall_score(normalized, topic_cfg)
            if (core_hits > 0 and anchor_hits > 0) or anchor_hits > 0 or recall_score >= 9:
                candidates.append(normalized)

    for feed in TOPIC_HTML_FEEDS.get(topic_key, []):
        for item in _parse_html_topic_page(feed["url"], feed["name"], limit=limit):
            normalized = _normalize_topic_feed_item(item)
            recall_score, core_hits, anchor_hits = _topic_recall_score(normalized, topic_cfg)
            if core_hits > 0 or anchor_hits > 0 or recall_score >= 6:
                candidates.append(normalized)

    return candidates


def get_news_health():
    checks = []
    seen = set()

    def add_check(name, kind, url):
        if not url or url in seen:
            return
        seen.add(url)
        started = time.time()
        try:
            r = requests.get(url, timeout=8, headers=HEADERS)
            elapsed_ms = int((time.time() - started) * 1000)
            ok = 200 <= r.status_code < 400
            content_type = r.headers.get("Content-Type", "")
            checks.append({
                "name": name,
                "kind": kind,
                "url": url,
                "ok": ok,
                "status_code": r.status_code,
                "elapsed_ms": elapsed_ms,
                "content_type": content_type,
                "error": "",
            })
        except Exception as e:
            elapsed_ms = int((time.time() - started) * 1000)
            checks.append({
                "name": name,
                "kind": kind,
                "url": url,
                "ok": False,
                "status_code": None,
                "elapsed_ms": elapsed_ms,
                "content_type": "",
                "error": str(e),
            })

    for topic_key, cfg in TOPIC_CONFIG.items():
        query_url = f"https://news.google.com/rss/search?q={quote_plus(cfg['query'])}&hl=en-US&gl=US&ceid=US:en"
        add_check(f"{topic_key}:google_news", "google_news", query_url)
        for idx, feed in enumerate(TOPIC_RSS_FEEDS.get(topic_key, []), start=1):
            add_check(f"{topic_key}:rss:{idx}", "rss", feed)
        for idx, feed in enumerate(TOPIC_HTML_FEEDS.get(topic_key, []), start=1):
            add_check(f"{topic_key}:html:{idx}", "html", feed["url"])

    add_check("translate_api", "http_api", "https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=zh-CN&dt=t&q=test")

    ok_count = sum(1 for item in checks if item["ok"])
    return {
        "checked_at": datetime.now(JST).isoformat(),
        "total": len(checks),
        "ok": ok_count,
        "failed": len(checks) - ok_count,
        "items": checks,
    }


def get_network_diagnostics():
    targets = [
        {"name": "google_news", "host": "news.google.com", "scheme": "https", "path": "/rss/search?q=test&hl=en-US&gl=US&ceid=US:en", "port": 443},
        {"name": "reuters_feed", "host": "feeds.reuters.com", "scheme": "https", "path": "/reuters/businessNews", "port": 443},
        {"name": "ap_feed", "host": "feeds.apnews.com", "scheme": "https", "path": "/rss/apf-business", "port": 443},
        {"name": "bbc_feed", "host": "feeds.bbci.co.uk", "scheme": "http", "path": "/news/business/rss.xml", "port": 80},
        {"name": "cnbc_feed", "host": "www.cnbc.com", "scheme": "https", "path": "/id/100003114/device/rss/rss.html", "port": 443},
        {"name": "translate_api", "host": "translate.googleapis.com", "scheme": "https", "path": "/translate_a/single?client=gtx&sl=auto&tl=zh-CN&dt=t&q=test", "port": 443},
    ]

    proxy_env = {
        key: os.environ.get(key, "")
        for key in ["HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "NO_PROXY", "http_proxy", "https_proxy", "all_proxy", "no_proxy"]
        if os.environ.get(key)
    }

    results = []
    for target in targets:
        host = target["host"]
        port = target["port"]
        url = f"{target['scheme']}://{host}{target['path']}"
        item = {
            "name": target["name"],
            "host": host,
            "port": port,
            "url": url,
            "dns_ok": False,
            "dns_addresses": [],
            "dns_error": "",
            "tcp_ok": False,
            "tcp_error": "",
            "http_ok": False,
            "http_status_code": None,
            "http_error": "",
        }

        try:
            addrinfo = socket.getaddrinfo(host, port, proto=socket.IPPROTO_TCP)
            addresses = sorted({entry[4][0] for entry in addrinfo if entry and entry[4]})
            item["dns_ok"] = bool(addresses)
            item["dns_addresses"] = addresses[:6]
        except Exception as e:
            item["dns_error"] = str(e)

        if item["dns_ok"]:
            try:
                with socket.create_connection((host, port), timeout=5):
                    item["tcp_ok"] = True
            except Exception as e:
                item["tcp_error"] = str(e)

        try:
            r = requests.get(url, timeout=8, headers=HEADERS)
            item["http_ok"] = 200 <= r.status_code < 400
            item["http_status_code"] = r.status_code
        except Exception as e:
            item["http_error"] = str(e)

        results.append(item)

    return {
        "checked_at": datetime.now(JST).isoformat(),
        "proxy_env": proxy_env,
        "targets": results,
    }


def _score_topic_item(item, topic_cfg):
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
        if _contains_term(title_text, keyword):
            core_topic_hits += 2
        elif _contains_term(body_text, keyword):
            core_topic_hits += 1
    for keyword in topic_cfg.get("anchor_terms", []):
        if _contains_term(title_text, keyword):
            anchor_hits += 2
        elif _contains_term(body_text, keyword):
            anchor_hits += 1
    for label, keywords in topic_cfg.get("drivers", {}).items():
        title_match = sum(1 for keyword in keywords if _contains_term(title_text, keyword))
        body_match = sum(1 for keyword in keywords if _contains_term(body_text, keyword))
        if body_match:
            score += min(12, title_match * 4 + body_match * 2.5)
            hits.append(label)
    for keyword in topic_cfg.get("topic_terms", []):
        if _contains_term(title_text, keyword):
            topic_hits += 2
        elif _contains_term(body_text, keyword):
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
    pub_dt = _pub_datetime_jst(item.get("pub_raw"))
    if pub_dt:
        age_hours = max(0.0, (datetime.now(JST) - pub_dt).total_seconds() / 3600.0)
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
    item["title_tokens"] = set(re.findall(r"[a-z0-9][a-z0-9+./&-]*", title_text))
    return item


def _topic_item_similarity(left, right):
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


def _select_diverse_topic_items(items, limit=3):
    remaining = sorted(items, key=lambda x: (x.get("digest_score", 0), x.get("sort_ts", 0)), reverse=True)
    selected = []
    while remaining and len(selected) < limit:
        best_idx = 0
        best_score = None
        for idx, item in enumerate(remaining):
            max_sim = max((_topic_item_similarity(item, picked) for picked in selected), default=0.0)
            mmr_score = item.get("digest_score", 0) - max_sim * 6
            if best_score is None or mmr_score > best_score:
                best_score = mmr_score
                best_idx = idx
        selected.append(remaining.pop(best_idx))
    return selected


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


def _sanitize_digest_items(items):
    sanitized = []
    for item in items:
        clean = dict(item)
        clean.pop("title_tokens", None)
        sanitized.append(clean)
    return sanitized


def _is_strong_topic_item(item):
    return (item.get("core_topic_hits") or 0) > 0 and (item.get("anchor_hits") or 0) > 0


def _is_medium_topic_item(item):
    anchor_hits = item.get("anchor_hits") or 0
    topic_hits = item.get("topic_hits") or 0
    driver_hits = item.get("driver_hits") or []
    return anchor_hits > 0 or (topic_hits >= 2 and len(driver_hits) >= 1)


def _is_macro_fallback_item(item):
    return (item.get("topic_hits") or 0) > 0 or (item.get("core_topic_hits") or 0) > 0 or (item.get("anchor_hits") or 0) > 0


def _is_topic_eligible(item):
    return _is_strong_topic_item(item) or _is_medium_topic_item(item) or _is_macro_fallback_item(item)


def fetch_topic_digest(topic):
    topic_key = (topic or "").strip().lower()
    if topic_key not in TOPIC_CONFIG:
        return {"error": "unknown topic"}

    cache_key = f"digest_{topic_key}"
    now = time.time()
    if cache_key in NEWS_CACHE and now - NEWS_CACHE[cache_key]["ts"] < TOPIC_DIGEST_TTL:
        return NEWS_CACHE[cache_key]["data"]

    cfg = TOPIC_CONFIG[topic_key]
    items = _fetch_topic_candidate_items(topic_key, cfg, limit=18)
    seen = set()
    deduped = []
    for item in items:
        key = re.sub(r"\s+", " ", (item.get("title", "")[:100]).lower()).strip()
        if key in seen:
            continue
        seen.add(key)
        deduped.append(_score_topic_item(item, cfg))

    now_jst = datetime.now(JST)
    same_day_items = [item for item in deduped if _is_today_jst(item.get("pub_raw"))]
    recent_items = []
    stale_items = []
    older_items = []
    for item in deduped:
        if item in same_day_items:
            continue
        pub_dt = _pub_datetime_jst(item.get("pub_raw"))
        if not pub_dt:
            stale_items.append(item)
            continue
        age = now_jst - pub_dt
        if age <= timedelta(hours=TOPIC_DIGEST_RECENT_HOURS):
            recent_items.append(item)
        elif age <= timedelta(days=TOPIC_DIGEST_MAX_AGE_DAYS):
            stale_items.append(item)
        elif age <= timedelta(days=TOPIC_DIGEST_OLDER_MAX_DAYS):
            older_items.append(item)

    same_day_items.sort(key=lambda x: (x.get("digest_score", 0), x.get("sort_ts", 0)), reverse=True)
    recent_items.sort(key=lambda x: (x.get("digest_score", 0), x.get("sort_ts", 0)), reverse=True)
    stale_items.sort(key=lambda x: (x.get("digest_score", 0), x.get("sort_ts", 0)), reverse=True)
    older_items.sort(key=lambda x: (x.get("digest_score", 0), x.get("sort_ts", 0)), reverse=True)

    same_day_strong = [item for item in same_day_items if _is_strong_topic_item(item)]
    recent_strong = [item for item in recent_items if _is_strong_topic_item(item)]
    stale_strong = [item for item in stale_items if _is_strong_topic_item(item)]
    older_strong = [item for item in older_items if _is_strong_topic_item(item)]
    same_day_medium = [item for item in same_day_items if item not in same_day_strong and _is_medium_topic_item(item)]
    recent_medium = [item for item in recent_items if item not in recent_strong and _is_medium_topic_item(item)]
    stale_medium = [item for item in stale_items if item not in stale_strong and _is_medium_topic_item(item)]
    older_medium = [item for item in older_items if item not in older_strong and _is_medium_topic_item(item)]

    selected_items = _select_diverse_topic_items(same_day_strong, limit=3)
    if len(selected_items) < 3:
        selected_items.extend(_select_diverse_topic_items(recent_strong, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        selected_items.extend(_select_diverse_topic_items(stale_strong, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        selected_items.extend(_select_diverse_topic_items(same_day_medium, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        selected_items.extend(_select_diverse_topic_items(recent_medium, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        selected_items.extend(_select_diverse_topic_items(stale_medium, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        selected_items.extend(_select_diverse_topic_items(older_strong, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        selected_items.extend(_select_diverse_topic_items(older_medium, limit=3 - len(selected_items)))
    if len(selected_items) < 3:
        macro_fallback = [
            item for item in (same_day_items + recent_items + stale_items + older_items)
            if item not in selected_items and _is_macro_fallback_item(item)
        ]
        selected_items.extend(_select_diverse_topic_items(macro_fallback, limit=3 - len(selected_items)))
    selected_items = [item for item in selected_items if _is_topic_eligible(item)]

    top_items = _sanitize_digest_items(_translate_digest_items(selected_items))

    driver_counts = {}
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
    for item in driver_source[:6]:
        for label in item.get("driver_hits", []):
            driver_counts[label] = driver_counts.get(label, 0) + 1
    drivers = [label for label, _ in sorted(driver_counts.items(), key=lambda kv: (-kv[1], kv[0]))[:3]]
    if not drivers and top_items:
        drivers = [cfg.get("fallback_driver", "最新动态")]

    tone, tone_class = _detect_digest_tone(top_items, cfg)
    result = {
        "topic": topic_key,
        "title": cfg["title"],
        "tone": tone,
        "tone_class": tone_class,
          "summary": _build_digest_summary(topic_key, tone, drivers),
          "drivers": drivers,
          "items": top_items,
          "updated": datetime.now(JST).strftime("%H:%M"),
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


@app.route("/api/news_health")
def news_health():
    return jsonify(get_news_health())


@app.route("/api/network_diagnostics")
def network_diagnostics():
    return jsonify(get_network_diagnostics())


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
