from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import Image, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle


BASE_DIR = Path(__file__).resolve().parent
OUTPUT_FILE = BASE_DIR / "用户指南_带截图版.pdf"


def register_font():
    candidates = [
        Path(r"C:\Windows\Fonts\msyh.ttc"),
        Path(r"C:\Windows\Fonts\msyh.ttf"),
        Path(r"C:\Windows\Fonts\simhei.ttf"),
        Path(r"C:\Windows\Fonts\simsun.ttc"),
    ]
    for path in candidates:
        if path.exists():
            pdfmetrics.registerFont(TTFont("GuideFont", str(path)))
            return "GuideFont"
    return "Helvetica"


def build_pdf():
    font_name = register_font()
    doc = SimpleDocTemplate(
        str(OUTPUT_FILE),
        pagesize=A4,
        leftMargin=16 * mm,
        rightMargin=16 * mm,
        topMargin=16 * mm,
        bottomMargin=16 * mm,
        title="日本股票观察系统用户指南（假数据示例版）",
        author="OpenAI Codex",
    )

    styles = getSampleStyleSheet()
    title_style = ParagraphStyle(
        "GuideTitle",
        parent=styles["Title"],
        fontName=font_name,
        fontSize=20,
        leading=26,
        textColor=colors.HexColor("#111827"),
        alignment=TA_LEFT,
        spaceAfter=10,
    )
    h1 = ParagraphStyle(
        "GuideH1",
        parent=styles["Heading1"],
        fontName=font_name,
        fontSize=14,
        leading=20,
        textColor=colors.HexColor("#1f3b8f"),
        spaceBefore=10,
        spaceAfter=6,
    )
    body = ParagraphStyle(
        "GuideBody",
        parent=styles["BodyText"],
        fontName=font_name,
        fontSize=10.5,
        leading=16,
        textColor=colors.HexColor("#111827"),
        spaceAfter=4,
    )

    story = []
    story.append(Paragraph("日本股票观察系统用户指南（假数据示例版）", title_style))
    story.append(Paragraph("说明：本文档基于当前已经完成的系统功能整理，文中股票、价格、盈亏、信号均为演示用假数据；截图来自本地演示页面。", body))
    story.append(Spacer(1, 4))

    sections = [
        ("1. 系统概览", [
            "当前系统包含 3 个主要页面：主页面、技术选股页面、个股图表页面。",
            "主页面负责看持仓、摘要和新闻；技术选股页面负责找机会；个股图表页面负责看 K 线和详细分析。",
        ]),
        ("2. 假数据示例", []),
        ("3. 主页面功能", [
            "支持持仓卡片显示，包括股票代码、日文名称、最新价格、涨跌幅、持股数、成本价、市值和盈亏。",
            "支持个股最新资讯、日经简报、半导体简报。",
            "右侧提供统一自选列表，可切换已持有、观察中和全部。",
        ]),
        ("4. 技术选股页面功能", [
            "支持股票池切换：核心 45、Nikkei 225、TOPIX Core 30。",
            "支持策略切换：超卖反弹、MACD 金叉、放量上涨、均线突破、综合信号。",
            "支持查看候选股票图表预览，并保持右侧自选列表统一。",
        ]),
        ("5. 个股图表页面功能", [
            "支持 K 线主图、成交量、MACD、周期切换（日线、周线、月线、4 小时）。",
            "支持左侧工具栏：普通查看、趋势线、水平线、斐波那契回撤、价格范围、日期范围、清空画线。",
            "支持个股资讯区与右侧共享自选列表。",
        ]),
        ("6. 右侧自选模块交互", [
            "单击：显示详情卡；在图表页会同步切换左侧 K 线。",
            "双击：直接进入该股票图表页。",
            "右键：当前支持编辑状态、加入观察。",
        ]),
        ("7. 数据保护", [
            "持仓数据保存在 portfolio.json。",
            "保存时使用原子写入，避免写到一半损坏主文件。",
            "每次保存前自动备份到 data_backups/，主文件损坏时会尝试读取最近可用备份。",
        ]),
        ("8. 推荐使用流程", [
            "先在主页面看持仓和摘要，再到技术选股页找机会，最后进入图表页细看。",
            "通过右侧自选列表快速切换股票，并用右键菜单维护状态。",
        ]),
    ]

    for title, bullets in sections:
        story.append(Paragraph(title, h1))
        if title == "2. 假数据示例":
            table_data = [
                ["代码", "名称", "状态", "持股数", "成本价", "最新价"],
                ["6758.T", "ソニーグループ", "已持有", "300", "¥3,754", "¥3,324"],
                ["7974.T", "任天堂", "已持有", "300", "¥9,256", "¥8,736"],
                ["9984.T", "ソフトバンクグループ", "观察中", "0", "¥0", "¥3,609"],
                ["3436.T", "SUMCO", "观察中", "0", "¥0", "¥1,738"],
            ]
            table = Table(table_data, colWidths=[24*mm, 42*mm, 20*mm, 18*mm, 24*mm, 24*mm])
            table.setStyle(TableStyle([
                ("FONTNAME", (0, 0), (-1, -1), font_name),
                ("FONTSIZE", (0, 0), (-1, -1), 9),
                ("BACKGROUND", (0, 0), (-1, 0), colors.HexColor("#e5e7eb")),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor("#111827")),
                ("GRID", (0, 0), (-1, -1), 0.5, colors.HexColor("#cbd5e1")),
                ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f8fafc")]),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
                ("LEFTPADDING", (0, 0), (-1, -1), 6),
                ("RIGHTPADDING", (0, 0), (-1, -1), 6),
                ("TOPPADDING", (0, 0), (-1, -1), 6),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
            ]))
            story.append(table)
            story.append(Spacer(1, 6))
            continue
        for bullet in bullets:
            story.append(Paragraph(f"• {bullet}", body))

    screenshots = [
        ("主页面示意图", BASE_DIR / "screenshots" / "01_dashboard.png", [
            "主页面用于快速查看持仓、新闻摘要与右侧自选列表。",
            "右侧自选列表支持单击查看详情、双击进入图表页、右键打开菜单。",
        ]),
        ("技术选股页面示意图", BASE_DIR / "screenshots" / "02_screener.png", [
            "技术选股页面支持股票池与策略切换。",
            "筛选结果会显示候选股票，并保持右侧自选模块统一。",
        ]),
        ("个股图表页面示意图", BASE_DIR / "screenshots" / "03_chart.png", [
            "个股图表页支持 K 线、成交量、MACD、画线工具和个股资讯。",
            "在图表页右侧点击自选股，左侧图表会同步切换。",
        ]),
    ]

    for caption, image_path, bullets in screenshots:
        story.append(Paragraph(caption, h1))
        if image_path.exists():
            img = Image(str(image_path))
            max_w = 170 * mm
            max_h = 92 * mm
            scale = min(max_w / img.imageWidth, max_h / img.imageHeight)
            img.drawWidth = img.imageWidth * scale
            img.drawHeight = img.imageHeight * scale
            story.append(img)
            story.append(Spacer(1, 5))
        for bullet in bullets:
            story.append(Paragraph(f"• {bullet}", body))

    doc.build(story)
    return OUTPUT_FILE


if __name__ == "__main__":
    output = build_pdf()
    print(output)
