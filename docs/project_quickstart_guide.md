# 项目快速了解文档

## 1. 项目定位

这是一个股票看盘与筛选项目，当前处于“Flask 页面层 + Spring Boot API 层”并行迁移阶段。

现在的推荐理解方式是：

- `server.py` 负责页面渲染和少量旧接口兼容
- `backend/` 负责主要数据接口、指标计算、聚合逻辑
- `static/js/` 负责前端展示、交互和图表绑定
- `templates/` 负责页面骨架

当前主方向已经明确：

- 数据抓取、数据处理、指标计算尽量放到 Spring Boot
- 前端只做展示和交互
- Flask 逐步退化为“页面壳”

## 2. 当前架构

### 2.1 运行形态

当前项目默认是双进程：

1. Flask 提供页面
2. Spring Boot 提供 `/api/*`

页面渲染后，前端会默认请求：

`http://localhost:8080/api/*`

这个默认值由 [server.py](/c:/Users/caisj/Desktop/stock_dashboard/server.py) 中的 `get_api_base()` 注入到模板：

- [index.html](/c:/Users/caisj/Desktop/stock_dashboard/templates/index.html)
- [chart.html](/c:/Users/caisj/Desktop/stock_dashboard/templates/chart.html)
- [screener.html](/c:/Users/caisj/Desktop/stock_dashboard/templates/screener.html)
- [short_interest.html](/c:/Users/caisj/Desktop/stock_dashboard/templates/short_interest.html)

如果你需要切换 API 地址，可以设置环境变量：

```powershell
$env:APP_API_BASE='http://localhost:8080'
```

### 2.2 前后端职责

Flask 层：

- 页面路由
- 模板渲染
- 个别未迁移调试接口

Spring Boot 层：

- screener
- chart history
- portfolio
- quotes / dashboard snapshot
- stock news
- stock insights
- ownership short
- topic digest
- trump news

前端 JS：

- 请求 API
- 渲染卡片、列表、图表
- loading / empty / error 状态
- watchlist 交互

## 3. 目录速览

### 3.1 根目录

- [server.py](/c:/Users/caisj/Desktop/stock_dashboard/server.py)
  Flask 页面层和旧逻辑入口
- [portfolio.json](/c:/Users/caisj/Desktop/stock_dashboard/portfolio.json)
  当前组合数据源
- [backend/](/c:/Users/caisj/Desktop/stock_dashboard/backend)
  Spring Boot 后端
- [static/](/c:/Users/caisj/Desktop/stock_dashboard/static)
  前端资源
- [templates/](/c:/Users/caisj/Desktop/stock_dashboard/templates)
  Jinja 页面模板
- [docs/](/c:/Users/caisj/Desktop/stock_dashboard/docs)
  项目文档

### 3.2 后端目录

Spring Boot 核心路径：

- [StockDashboardBackendApplication.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/StockDashboardBackendApplication.java)
- [controller/](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/controller)
- [service/](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service)
- [service/impl/](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl)
- [client/](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/client)
- [repository/](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/repository)
- [dto/response/](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/dto/response)

推荐你看代码的顺序：

1. `controller`
2. `service/impl`
3. `client`
4. `dto/response`

### 3.3 前端目录

重点 JS 文件：

- [utils.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/utils.js)
  通用工具、`fetchJson`、API 兼容层
- [portfolio.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/portfolio.js)
  首页持仓卡片和摘要
- [watchlist.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/watchlist.js)
  右侧观察列表
- [screener.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/screener.js)
  筛选页
- [chart_page.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/chart_page.js)
  图表页
- [news.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/news.js)
  首页新闻
- [digest.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/digest.js)
  首页 digest 面板
- [short_interest_page.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/short_interest_page.js)
  空头调试页

## 4. 页面与接口映射

### 4.1 首页 `/`

模板：

- [index.html](/c:/Users/caisj/Desktop/stock_dashboard/templates/index.html)

核心 JS：

- [portfolio.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/portfolio.js)
- [watchlist.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/watchlist.js)
- [news.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/news.js)
- [digest.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/digest.js)

主要接口：

- `/api/dashboard_snapshot`
- `/api/quotes`
- `/api/index_quotes`
- `/api/portfolio`
- `/api/add_stock`
- `/api/remove_stock`
- `/api/stock_news`
- `/api/trump_news`
- `/api/topic_digest`

### 4.2 筛选页 `/screener`

模板：

- [screener.html](/c:/Users/caisj/Desktop/stock_dashboard/templates/screener.html)

核心 JS：

- [screener.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/screener.js)

主要接口：

- `/api/screener`
- `/api/add_stock`
- `/api/remove_stock`

### 4.3 图表页 `/chart`

模板：

- [chart.html](/c:/Users/caisj/Desktop/stock_dashboard/templates/chart.html)

核心 JS：

- [chart_page.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/chart_page.js)
- [watchlist.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/watchlist.js)

主要接口：

- `/api/chart_history`
- `/api/stock_insights`
- `/api/ownership_short`
- `/api/stock_news`

### 4.4 空头页 `/short-interest`

模板：

- [short_interest.html](/c:/Users/caisj/Desktop/stock_dashboard/templates/short_interest.html)

核心 JS：

- [short_interest_page.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/short_interest_page.js)

主要接口：

- `/api/ownership_short`
- Flask 旧调试接口 `/api/ownership_short_debug`

注意：

这个页面还没有完全脱离 Flask，因为 debug 接口还在 Python 侧。

## 5. Spring Boot 后端主线

### 5.1 控制器

- [SystemController.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/controller/SystemController.java)
  健康检查和迁移状态
- [ScreenerController.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/controller/ScreenerController.java)
  筛选接口
- [ChartController.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/controller/ChartController.java)
  图表历史
- [PortfolioController.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/controller/PortfolioController.java)
  组合读写
- [MarketController.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/controller/MarketController.java)
  quotes / index / dashboard snapshot
- [ResearchController.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/controller/ResearchController.java)
  新闻、洞察、ownership、digest、trump news

### 5.2 核心服务

- [IndicatorServiceImpl.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl/IndicatorServiceImpl.java)
  RSI / SMA / MACD
- [ChartServiceImpl.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl/ChartServiceImpl.java)
  K 线、分组压缩、指标序列
- [ScreenerServiceImpl.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl/ScreenerServiceImpl.java)
  筛选逻辑
- [QuoteServiceImpl.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl/QuoteServiceImpl.java)
  持仓报价、指数报价
- [DashboardServiceImpl.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl/DashboardServiceImpl.java)
  首页快照聚合
- [PortfolioServiceImpl.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl/PortfolioServiceImpl.java)
  组合增删改
- [NewsServiceImpl.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl/NewsServiceImpl.java)
  个股新闻
- [InsightsServiceImpl.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl/InsightsServiceImpl.java)
  图表页洞察卡片
- [OwnershipServiceImpl.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl/OwnershipServiceImpl.java)
  ownership / short interest 卡片
- [MacroNewsServiceImpl.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl/MacroNewsServiceImpl.java)
  trump news 和 topic digest

### 5.3 Client 层

- [YahooFinanceClient.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/client/YahooFinanceClient.java)
  Yahoo 行情、quote summary、个股 RSS
- [RssFeedClient.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/client/RssFeedClient.java)
  通用 RSS 拉取

### 5.4 Repository 层

- [PortfolioRepository.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/repository/PortfolioRepository.java)
- [JsonPortfolioRepository.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/repository/file/JsonPortfolioRepository.java)

当前组合还是落在 [portfolio.json](/c:/Users/caisj/Desktop/stock_dashboard/portfolio.json)。

## 6. 关键设计点

### 6.1 API 统一响应

Spring Boot 统一返回 [ApiResponse.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/api/ApiResponse.java)：

```json
{
  "success": true,
  "code": "OK",
  "message": null,
  "timestamp": "...",
  "data": {}
}
```

前端在 [utils.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/utils.js) 中会自动：

- 解包 `data`
- 兼容旧 Flask 直接返回 JSON
- 自动补 camelCase / snake_case 别名

所以现在前端老代码和新后端 DTO 可以并存。

### 6.2 页面如何切到新后端

核心逻辑在 [utils.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/utils.js)：

- `getApiBase()`
- `apiUrl()`
- `fetchJson()`
- `unwrapApiPayload()`

页面模板会写入 `data-api-base`，前端请求 `/api/*` 时自动拼接到 Spring Boot 地址。

### 6.3 缓存

配置在 [application.yml](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/resources/application.yml)。

当前使用 Spring Cache + Caffeine，已经建好的 cache name 包括：

- `quotes`
- `indexQuotes`
- `chartHistory`
- `stockNews`
- `stockInsights`
- `topicDigest`
- `screener`
- `ownershipShort`
- `dashboardSnapshot`

## 7. 启动方式

### 7.1 推荐启动顺序

先启动 Spring Boot：

```powershell
cd c:\Users\caisj\Desktop\stock_dashboard\backend
mvn spring-boot:run
```

再启动 Flask 页面层：

```powershell
cd c:\Users\caisj\Desktop\stock_dashboard
python server.py
```

### 7.2 常用验证

Spring Boot 测试：

```powershell
cd c:\Users\caisj\Desktop\stock_dashboard\backend
mvn test
```

Flask 语法检查：

```powershell
cd c:\Users\caisj\Desktop\stock_dashboard
python -m py_compile server.py
```

## 8. 当前迁移状态

已经完成：

- Spring Boot 骨架
- screener
- chart history
- portfolio
- dashboard snapshot
- quotes / index quotes
- stock news
- stock insights
- ownership short
- topic digest
- trump news 基础版
- 前端 API base 切换

仍然保留在 Flask 的内容：

- 页面渲染
- `ownership_short_debug` 之类的调试接口
- 一部分历史 Python 抓取逻辑

## 9. 已知情况

### 9.1 `trump_news`

接口已经迁到 Spring Boot，但当前 RSS 命中率不稳定，可能返回空数组。

文件：

- [MacroNewsServiceImpl.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl/MacroNewsServiceImpl.java)

### 9.2 `ownership_short_debug`

短线调试页还依赖 Flask 的 debug 接口，所以这条链路没有完全迁完。

前端文件：

- [short_interest_page.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/short_interest_page.js)

### 9.3 Yahoo `quoteSummary`

某些环境下会返回 `401`。当前 Java 后端已经做了降级处理，不会让图表页直接崩掉，但部分基本面字段可能显示 `--`。

## 10. 建议你接手时的阅读顺序

如果你只有 30 分钟，建议这样看：

1. [server.py](/c:/Users/caisj/Desktop/stock_dashboard/server.py)
2. [utils.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/utils.js)
3. [portfolio.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/portfolio.js)
4. [chart_page.js](/c:/Users/caisj/Desktop/stock_dashboard/static/js/chart_page.js)
5. [ResearchController.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/controller/ResearchController.java)
6. [ChartServiceImpl.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl/ChartServiceImpl.java)
7. [ScreenerServiceImpl.java](/c:/Users/caisj/Desktop/stock_dashboard/backend/src/main/java/com/caisj/stockdashboard/backend/service/impl/ScreenerServiceImpl.java)

如果你要继续做迁移，建议从这里开始：

1. 把 `ownership_short_debug` 迁到 Spring Boot
2. 补强 `trump_news` 数据源
3. 逐步把 Flask 中旧 `/api/*` 彻底下线
4. 最后再决定是否把页面层也迁到 Java 或独立前端框架
