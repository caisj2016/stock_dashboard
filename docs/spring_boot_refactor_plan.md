# Spring Boot Refactor Plan

## Goal

Move data fetching, data processing, indicator calculation, and aggregation logic from `server.py` into a dedicated Spring Boot backend.

Keep the current frontend focused on:

- page rendering
- user interaction
- loading and error states
- chart library binding

## Migration Order

1. Project skeleton and shared response model
2. Screener
3. Chart history and technical indicators
4. Portfolio persistence
5. Dashboard snapshot
6. News, digest, and ownership
7. Frontend API base switch and compatibility layer

## Current Backend Target Map

- `run_screener` -> `ScreenerService`
- `_calc_screener_metrics` -> `ScreenerService` + `IndicatorService`
- `fetch_history` -> `MarketDataClient`
- `build_chart_history` -> `ChartService`
- `_calc_rsi` / `_calc_macd` / `_sma_series` -> `IndicatorService`
- `load_portfolio` / `save_portfolio` -> `PortfolioRepository`
- `get_dashboard_snapshot_data` -> `DashboardService`
- `fetch_stock_news` / `fetch_topic_digest` / `fetch_trump_news` -> `NewsService` / `MacroNewsService`
- `fetch_stock_insights` -> `InsightsService`
- `fetch_ownership_short` -> `OwnershipService`

## Initial Endpoints

- `GET /api/healthz`
- `GET /api/migration/status`
- `GET /api/screener`
- `GET /api/chart-history`
- `GET /api/portfolio`
- `GET /api/dashboard_snapshot`
- `GET /api/stock_news`
- `GET /api/stock_insights`
- `GET /api/ownership_short`
- `GET /api/topic_digest`
- `GET /api/trump_news`

## Notes

The main frontend pages now support a shared `data-api-base` / `APP_API_BASE` switch, and default to `http://localhost:8080` so the Flask-rendered pages call the Spring Boot backend by default.
