import io
import re
from datetime import datetime
from html import unescape
from urllib.parse import quote_plus

import pandas as pd
import requests


def is_japan_stock_symbol(symbol):
    text = (symbol or "").strip().upper()
    return text.endswith(".T") and bool(text[:-2])


def japan_code_from_symbol(symbol):
    text = (symbol or "").strip().upper()
    return text[:-2] if text.endswith(".T") else text


def normalize_scrape_text(value):
    if value is None:
        return ""
    try:
        if pd.isna(value):
            return ""
    except Exception:
        pass
    text = str(value).replace("\xa0", " ").replace("\r", " ").replace("\n", " ")
    return " ".join(text.split()).strip()


def _flatten_table_columns(columns):
    if hasattr(columns, "to_flat_index"):
        flat_index = columns.to_flat_index()
    else:
        flat_index = columns
    result = []
    for col in flat_index:
        parts = col if isinstance(col, tuple) else (col,)
        cleaned = []
        for part in parts:
            text = normalize_scrape_text(part)
            if not text:
                continue
            if text.lower().startswith("unnamed:"):
                continue
            cleaned.append(text)
        result.append(" ".join(cleaned).strip())
    return result


def _read_html_tables(html):
    try:
        return pd.read_html(io.StringIO(html))
    except Exception:
        return []


def _fetch_japan_kabuka_page(url, headers):
    request_headers = dict(headers or {})
    request_headers["Accept-Language"] = "ja,en-US;q=0.9,en;q=0.8"
    response = requests.get(url, timeout=12, headers=request_headers)
    response.raise_for_status()
    return response.text


def _find_table_value(row, columns, candidate_terms):
    normalized_columns = [
        col.replace("\u3000", " ").replace("/", " ").replace("-", " ").lower()
        for col in columns
    ]
    for terms in candidate_terms:
        normalized_terms = [term.lower() for term in terms if term]
        for idx, column_text in enumerate(normalized_columns):
            if all(term in column_text for term in normalized_terms):
                return normalize_scrape_text(row.get(columns[idx]))
    return ""


def _tone_from_text(value):
    text = normalize_scrape_text(value)
    if not text:
        return "neutral"
    if text.startswith("+") or re.search(r"\+\d", text):
        return "up"
    if text.startswith("-") or re.search(r"-\d", text):
        return "down"
    return "neutral"


def _pick_first_row_with_content(df, columns, min_hits=3):
    for _, row in df.iterrows():
        hits = sum(1 for column in columns if normalize_scrape_text(row.get(column)))
        if hits >= min_hits:
            return row
    return None


def _strip_html_text(fragment):
    text = re.sub(r"(?i)<br\s*/?>", " ", str(fragment or " "))
    text = re.sub(r"<[^>]+>", " ", text)
    return normalize_scrape_text(unescape(text))


def _extract_table_html(html, table_id=None, table_class=None):
    pattern = r"<table[^>]*"
    if table_id:
        pattern += rf'id=["\']{re.escape(table_id)}["\'][^>]*'
    if table_class:
        pattern += rf'class=["\'][^"\']*{re.escape(table_class)}[^"\']*["\'][^>]*'
    pattern += r">.*?</table>"
    match = re.search(pattern, html, re.I | re.S)
    return match.group(0) if match else ""


def _extract_row_cells(table_html):
    rows = []
    for row_html in re.findall(r"<tr[^>]*>(.*?)</tr>", table_html or "", re.I | re.S):
        cells = re.findall(r"<t[dh][^>]*>(.*?)</t[dh]>", row_html, re.I | re.S)
        cleaned = [_strip_html_text(cell) for cell in cells]
        if any(cleaned):
            rows.append(cleaned)
    return rows


def _parse_japan_kabuka_overview_html(html):
    table_html = _extract_table_html(html, table_id="myTable")
    rows = _extract_row_cells(table_html)
    if len(rows) < 3:
        return {}
    latest = rows[2]
    if len(latest) < 10:
        return {}
    return {
        "date": latest[0],
        "institution_delta": latest[2],
        "credit_sell": latest[3],
        "credit_buy": latest[4],
        "jsf_sell_balance": latest[5],
        "jsf_buy_balance": latest[6],
        "rotation_days": latest[7],
        "shortage_shares": latest[8],
        "reverse_fee": latest[9],
    }


def _parse_japan_kabuka_detail_html(html):
    snapshot = {}
    list_table = _extract_table_html(html, table_class="kikann-table")
    list_rows = _extract_row_cells(list_table)
    if len(list_rows) >= 2:
        snapshot["institution_count"] = str(max(0, len(list_rows) - 1))

    detail_table = _extract_table_html(html, table_id="myTable")
    detail_rows = _extract_row_cells(detail_table)
    if len(detail_rows) >= 3:
        latest = detail_rows[2]
        if len(latest) >= 6:
            snapshot["detail_date"] = latest[0]
            snapshot["institution_total_delta"] = latest[-3]
            snapshot["institution_sell_total"] = latest[-2]
            snapshot["institution_buy_total"] = latest[-1]
    return snapshot


def _is_effectively_zero_text(value):
    text = normalize_scrape_text(value)
    if not text or text == "-":
        return True
    compact = re.sub(r"[^\d%()+\-.,]", "", text.replace(" ", ""))
    compact = compact.replace("%", "").replace("(", "").replace(")", "")
    compact = compact.replace("+", "").replace("-", "").replace(".", "").replace(",", "")
    return compact == "" or set(compact) <= {"0"}


def collect_japan_kabuka_debug(symbol, *, headers, jst, fetch_ownership_short):
    symbol = (symbol or "").strip().upper()
    code = japan_code_from_symbol(symbol)
    overview_url = f"https://japan-kabuka.com/gyakuhibuChart/?id={quote_plus(code)}"
    detail_url = f"https://japan-kabuka.com/chart_detail?id={quote_plus(code)}"
    snapshot = {
        "ok": True,
        "symbol": symbol,
        "market": "jp",
        "requested_at": datetime.now(jst).isoformat(),
        "source": {
            "provider": "japan-kabuka.com",
            "overview_url": overview_url,
            "detail_url": detail_url,
            "overview_fetch_ok": False,
            "detail_fetch_ok": False,
            "overview_error": "",
            "detail_error": "",
        },
        "raw": {},
        "derived": {},
        "diagnosis": "",
        "missing_fields": [],
    }

    overview_html = ""
    detail_html = ""
    try:
        overview_html = _fetch_japan_kabuka_page(overview_url, headers)
        snapshot["source"]["overview_fetch_ok"] = True
    except Exception as exc:
        snapshot["source"]["overview_error"] = str(exc)
    try:
        detail_html = _fetch_japan_kabuka_page(detail_url, headers)
        snapshot["source"]["detail_fetch_ok"] = True
    except Exception as exc:
        snapshot["source"]["detail_error"] = str(exc)

    overview_table = _extract_table_html(overview_html, table_id="myTable") if overview_html else ""
    detail_list_table = _extract_table_html(detail_html, table_class="kikann-table") if detail_html else ""
    detail_main_table = _extract_table_html(detail_html, table_id="myTable") if detail_html else ""
    overview_rows = _extract_row_cells(overview_table)
    detail_list_rows = _extract_row_cells(detail_list_table)
    detail_rows = _extract_row_cells(detail_main_table)

    overview = _parse_japan_kabuka_overview_html(overview_html) if overview_html else {}
    detail = _parse_japan_kabuka_detail_html(detail_html) if detail_html else {}

    snapshot["raw"] = {
        "overview_latest_row": overview_rows[2] if len(overview_rows) >= 3 else [],
        "detail_latest_row": detail_rows[2] if len(detail_rows) >= 3 else [],
        "overview_recent_rows": overview_rows[2:10] if len(overview_rows) >= 3 else [],
        "detail_recent_rows": detail_rows[2:10] if len(detail_rows) >= 3 else [],
        "institution_list_preview": detail_list_rows[1:6] if len(detail_list_rows) >= 2 else [],
        "overview_parsed": overview,
        "detail_parsed": detail,
    }

    zero_like_fields = {
        key: value
        for key, value in overview.items()
        if key in {"credit_sell", "credit_buy", "jsf_sell_balance", "jsf_buy_balance", "shortage_shares", "reverse_fee"}
    }
    zero_like_results = {key: _is_effectively_zero_text(value) for key, value in zero_like_fields.items()}

    missing_fields = []
    for key, value in {
        "date": overview.get("date"),
        "institution_delta": overview.get("institution_delta"),
        "credit_sell": overview.get("credit_sell"),
        "credit_buy": overview.get("credit_buy"),
        "jsf_sell_balance": overview.get("jsf_sell_balance"),
        "jsf_buy_balance": overview.get("jsf_buy_balance"),
        "shortage_shares": overview.get("shortage_shares"),
        "reverse_fee": overview.get("reverse_fee"),
        "institution_count": detail.get("institution_count"),
    }.items():
        if not normalize_scrape_text(value):
            missing_fields.append(key)

    snapshot["missing_fields"] = missing_fields
    snapshot["derived"] = {
        "overview_row_cells": len(snapshot["raw"]["overview_latest_row"]),
        "detail_row_cells": len(snapshot["raw"]["detail_latest_row"]),
        "institution_list_count": max(0, len(detail_list_rows) - 1),
        "overview_zero_like_map": zero_like_results,
        "all_balance_fields_zero_like": bool(zero_like_results) and all(zero_like_results.values()),
        "has_parsed_overview": bool(overview),
        "has_parsed_detail": bool(detail),
        "final_payload_provider": "japan-kabuka.com",
    }

    if snapshot["source"]["overview_error"] or snapshot["source"]["detail_error"]:
        snapshot["diagnosis"] = "抓取失败，请优先检查 overview_error / detail_error"
    elif not overview and not detail:
        snapshot["diagnosis"] = "页面可达，但没有解析出概览或明细，可能是页面结构变化或表格定位失败"
    elif snapshot["derived"]["all_balance_fields_zero_like"]:
        snapshot["diagnosis"] = "抓取成功，但最新一行主要余额字段都接近 0 或空，优先确认目标日期是否休市、页面是否只展示占位值"
    elif missing_fields:
        snapshot["diagnosis"] = "解析到部分字段，但仍有关键字段缺失，建议优先核对原始行和解析映射"
    else:
        snapshot["diagnosis"] = "抓取和解析看起来正常，前端展示异常时建议继续检查最终 payload 组装"

    try:
        snapshot["final_payload"] = fetch_ownership_short(symbol)
    except Exception as exc:
        snapshot["final_payload"] = {"ok": False, "error": str(exc)}
    return snapshot


def fetch_japan_kabuka_ownership_short(symbol, *, headers, jst, build_insight_item):
    code = japan_code_from_symbol(symbol)
    overview_url = f"https://japan-kabuka.com/gyakuhibuChart/?id={quote_plus(code)}"
    detail_url = f"https://japan-kabuka.com/chart_detail?id={quote_plus(code)}"
    try:
        overview_html = _fetch_japan_kabuka_page(overview_url, headers)
        detail_html = _fetch_japan_kabuka_page(detail_url, headers)
    except Exception as exc:
        return {"ok": False, "error": f"日本空卖数据抓取失败: {exc}"}

    overview = _parse_japan_kabuka_overview_html(overview_html)
    detail = _parse_japan_kabuka_detail_html(detail_html)

    latest_date = detail.get("detail_date") or overview.get("date") or ""
    has_institutional = any(
        normalize_scrape_text(detail.get(key))
        for key in ["institution_count", "institution_total_delta", "institution_sell_total", "institution_buy_total"]
    ) or bool(normalize_scrape_text(overview.get("institution_delta")))
    has_short_interest = any(
        normalize_scrape_text(overview.get(key))
        for key in ["credit_sell", "credit_buy", "jsf_sell_balance", "jsf_buy_balance", "shortage_shares", "reverse_fee"]
    )

    if not has_institutional and not has_short_interest:
        return {"ok": False, "error": "抓取成功但暂未解析到有效机构或券空数据"}

    cards = [
        {
            "key": "institutional_short",
            "title": "机构空卖",
            "subtitle": "展示日本空卖网站里的机构空卖概要",
            "items": [
                build_insight_item("最新统计日", latest_date or "暂无覆盖", "neutral", "来源: chart_detail / gyakuhibuChart"),
                build_insight_item("参与机构数", detail.get("institution_count") or "暂无覆盖", "neutral", "统计空卖明细页出现的机构数量"),
                build_insight_item(
                    "机构总变动",
                    detail.get("institution_total_delta") or overview.get("institution_delta") or "暂无覆盖",
                    _tone_from_text(detail.get("institution_total_delta") or overview.get("institution_delta")),
                    "机构空卖明细页汇总变动，正负号表示方向",
                ),
                build_insight_item(
                    "机构卖出 / 买入",
                    " / ".join(
                        [
                            detail.get("institution_sell_total") or "卖出: 暂无覆盖",
                            detail.get("institution_buy_total") or "买入: 暂无覆盖",
                        ]
                    ),
                    "neutral",
                    "来源: 机构空卖明细页最新一行",
                ),
            ],
        },
        {
            "key": "credit_balance",
            "title": "信用日证金",
            "subtitle": "信用卖买与日证金余额",
            "items": [
                build_insight_item("信用卖", overview.get("credit_sell") or "暂无覆盖", "neutral", "来源: gyakuhibuChart"),
                build_insight_item("信用买", overview.get("credit_buy") or "暂无覆盖", "neutral", "来源: gyakuhibuChart"),
                build_insight_item("日证金卖残", overview.get("jsf_sell_balance") or "暂无覆盖", "neutral", "来源: gyakuhibuChart"),
                build_insight_item("日证金买残", overview.get("jsf_buy_balance") or "暂无覆盖", "neutral", "来源: gyakuhibuChart"),
            ],
        },
        {
            "key": "reverse_fee",
            "title": "逆日步与不足股",
            "subtitle": "不足股数与逆日步变化",
            "items": [
                build_insight_item("不足股数", overview.get("shortage_shares") or "暂无覆盖", _tone_from_text(overview.get("shortage_shares")), "来源: gyakuhibuChart"),
                build_insight_item("逆日步", overview.get("reverse_fee") or "暂无覆盖", _tone_from_text(overview.get("reverse_fee")), "来源: gyakuhibuChart"),
                build_insight_item("数据源", "japan-kabuka.com", "neutral", code),
            ],
        },
    ]

    return {
        "ok": True,
        "symbol": symbol,
        "title": "机构与空头结构",
        "updated": datetime.now(jst).strftime("%H:%M"),
        "coverage": {
            "has_institutional": has_institutional,
            "has_short_interest": has_short_interest,
            "data_quality": "medium" if has_institutional and has_short_interest else "low",
        },
        "source": {
            "provider": "japan-kabuka.com",
            "overview_url": overview_url,
            "detail_url": detail_url,
        },
        "cards": cards,
    }
