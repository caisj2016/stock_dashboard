# 日本股票观察仪表盘

一个基于 `Flask + yfinance + 原生前端` 的日股观察项目，支持：

- 持仓与观察股管理
- 首页报价与新闻摘要
- 技术选股页面
- 个股 K 线图表页
- 右侧统一自选列表

## 运行环境

- Python 3.10+
- Windows / macOS / Linux

安装依赖：

```bash
pip install flask flask-cors yfinance requests
```

启动项目：

```bash
python server.py
```

默认地址：

```text
http://localhost:5555
```

## 配置文件

项目支持 `.env` 配置。

常用配置示例：

```env
HOST=0.0.0.0
PORT=5555
FLASK_DEBUG=1
TEMPLATES_AUTO_RELOAD=1
QUOTE_CACHE_TTL=60
NEWS_CACHE_TTL=300
TRUMP_CACHE_TTL=600
HISTORY_FETCH_TIMEOUT=12
SCREENER_FETCH_WORKERS=8
PORTFOLIO_BACKUP_LIMIT=20
```

参考模板见：

- [`.env.example`](./.env.example)

## 数据存储

当前项目不是数据库存储，核心数据保存在本地文件：

- `portfolio.json`

这里保存的是你的股票列表与状态，例如：

- 股票代码
- 股票名称
- `已持有 / 观察中`
- 持股数
- 成本价

## 数据保护

项目现在已经加入了基础数据保护机制，主要针对 `portfolio.json`：

1. 原子写入
   保存时会先写入临时文件，再替换正式文件，避免写到一半损坏主文件。

2. 自动备份
   每次成功保存前，会把旧的 `portfolio.json` 备份到：

   - `data_backups/`

3. 备份数量控制
   默认最多保留 `20` 份备份，可通过 `.env` 里的 `PORTFOLIO_BACKUP_LIMIT` 调整。

4. 备份回退
   如果主文件损坏、JSON 解析失败，服务会尝试读取最近一份可用备份。

## Git 忽略

以下本地数据默认不会提交到 Git：

- `.env`
- `portfolio.json`
- `.yf_cache/`
- `data_backups/`
- `__pycache__/`

规则见：

- [`.gitignore`](./.gitignore)

## 项目结构

```text
stock_dashboard/
├─ server.py
├─ portfolio.json
├─ templates/
│  ├─ index.html
│  ├─ screener.html
│  ├─ chart.html
│  └─ partials/
├─ static/
│  ├─ css/
│  └─ js/
├─ .env
├─ .env.example
└─ README.md
```

## 页面说明

### 首页

- 持仓卡片
- 个股新闻
- 日经 / 半导体摘要
- 右侧统一自选列表

### 技术选股

- 支持股票池切换
- 支持多种技术策略筛选
- 结果可跳转到图表页

### 个股图表

- K 线主图
- 成交量 / MACD 开关
- 右侧统一自选列表
- 个股最新资讯

## 开发说明

如果开启：

```env
FLASK_DEBUG=1
```

通常修改以下内容后会自动重载：

- `server.py`
- `templates/*`
- `static/js/*`
- `static/css/*`

## 已初始化 Git

项目已经初始化 Git 并上传到：

- `https://github.com/caisj2016/stock_dashboard.git`

---

如果后续你想继续做，我建议下一步优先补：

- 备份恢复入口
- 自选列表详情卡优化
- 图表页中文文案统一清理
