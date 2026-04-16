# Japan Stock Portfolio Dashboard

日本株のポートフォリオ監視、個別銘柄チャート、スクリーニング、ニュース、信用残・空売り系の指標をまとめて見るためのローカルダッシュボードです。

現在のメイン実装は `backend/` の Spring Boot アプリです。画面テンプレートと静的ファイルも Spring Boot 側に配置されており、通常は `start.bat` または `run-dev.cmd` から起動します。

## 主な機能

- 保有銘柄・監視銘柄の一覧表示
- 日本株の株価、前日比、指数、簡易ダッシュボード表示
- 個別銘柄チャート、出来高、RSI、SMA、MACD
- スクリーナーによる候補銘柄抽出
- 個別銘柄ニュース、トピック digest、マクロ関連ニュース
- 信用残・空売り関連データの表示
- `portfolio.json` によるローカル保存と自動バックアップ

## 必要環境

- Java 17
- Maven 3.8+
- Windows では `start.bat` / `run-dev.cmd` が利用できます

外部データ取得には Yahoo Finance などの公開データソースを利用します。ネットワーク状況や提供元の仕様変更により、一部項目が空になることがあります。

## 起動方法

### 推奨: 自動起動

```bat
start.bat
```

`start.bat` は以下を行います。

- `18080` から `18090` の空きポートを探す
- Maven があれば Spring Boot を dev モードで起動する
- Maven がない場合はビルド済み jar を使って起動する
- ブラウザで `http://localhost:<port>` を開く

### 開発用ホットリロード

```bat
run-dev.cmd
```

固定ポート `18080` で Spring Boot を `dev` profile にして起動します。

### jar から起動

事前にビルドします。

```powershell
cd backend
mvn -q -DskipTests package
```

その後、リポジトリ直下で起動します。

```bat
run.cmd
```

## URL

標準の画面 URL は次の通りです。

- ダッシュボード: `http://localhost:18080/`
- スクリーナー: `http://localhost:18080/screener`
- 個別チャート: `http://localhost:18080/chart?symbol=6758.T`
- 信用残・空売り: `http://localhost:18080/short-interest?symbol=6758.T`
- API ドキュメント: `http://localhost:18080/swagger-ui/index.html`
- ヘルスチェック: `http://localhost:18080/api/healthz`

`start.bat` を使った場合は、実際のポートが `18080` 以外になることがあります。

## 設定

Spring Boot の主な設定は [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml) にあります。

よく使う環境変数:

```env
BACKEND_PORT=18080
APP_ALLOWED_ORIGINS=http://localhost:5555,http://localhost:3000
APP_PORTFOLIO_FILE=../portfolio.json
APP_PORTFOLIO_BACKUP_DIR=../data_backups
PORTFOLIO_BACKUP_LIMIT=20
HISTORY_FETCH_TIMEOUT=12
APP_YAHOO_CHART_BASE_URL=https://query1.finance.yahoo.com/v8/finance/chart
```

開発用 profile は [backend/src/main/resources/application-dev.yml](backend/src/main/resources/application-dev.yml) で、Thymeleaf と静的リソースのキャッシュを無効化しています。

`.env.example` は旧 Flask 実装由来の設定も含みます。現在の通常起動では Spring Boot の `application.yml` と環境変数が優先です。

## データ保存

ローカルデータは主に以下に保存されます。

- `portfolio.json`: 保有銘柄・監視銘柄データ
- `data_backups/`: `portfolio.json` の自動バックアップ
- `.yf_cache/`: 取得データのローカルキャッシュ

`portfolio.json` とバックアップは個人データを含む可能性があるため、Git 管理対象から外しています。

## テストとビルド

```powershell
cd backend
mvn test
```

コンパイルだけ確認する場合:

```powershell
cd backend
mvn -q -DskipTests compile
```

jar を作成する場合:

```powershell
cd backend
mvn -q -DskipTests package
```

## 主な API

レスポンスは基本的に `ApiResponse` 形式です。

```json
{
  "success": true,
  "code": "OK",
  "message": null,
  "timestamp": "...",
  "data": {}
}
```

代表的なエンドポイント:

- `GET /api/dashboard_snapshot`
- `GET /api/quotes`
- `GET /api/index_quotes`
- `GET /api/chart-history`
- `GET /api/chart_history`
- `GET /api/screener`
- `GET /api/portfolio`
- `POST /api/portfolio`
- `POST /api/add_stock`
- `POST /api/remove_stock`
- `GET /api/stock_news`
- `GET /api/stock_insights`
- `GET /api/ownership_short`
- `GET /api/ownership_short_debug`
- `GET /api/trump_news`
- `GET /api/topic_digest`
- `GET /api/migration/status`

詳細は起動後の Swagger UI を参照してください。

## ディレクトリ構成

```text
stock_dashboard/
├─ backend/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/caisj/stockdashboard/backend/
│     │  ├─ controller/
│     │  ├─ service/
│     │  ├─ client/
│     │  ├─ repository/
│     │  ├─ domain/
│     │  └─ dto/
│     └─ main/resources/
│        ├─ templates/
│        ├─ static/
│        ├─ application.yml
│        └─ application-dev.yml
├─ docs/
├─ python_fetchers/
├─ portfolio.json
├─ data_backups/
├─ start.bat
├─ run-dev.cmd
├─ run.cmd
└─ README.md
```

リポジトリ直下の `templates/` と `static/` は旧構成の互換・参照用ファイルを含みます。現在の Spring Boot 実行時は `backend/src/main/resources/templates/` と `backend/src/main/resources/static/` が使用されます。

## 開発メモ

- Java 側は Spring Boot 3.3.4、Thymeleaf、Caffeine cache、springdoc-openapi を使用しています。
- `controller` はリクエスト受付、`service` / `service.impl` は業務ロジック、`client` は外部データ取得、`repository` はローカル保存を担当します。
- フロントエンド JS は Spring Boot の静的リソースとして配信され、`/api/*` を呼び出します。
- 旧 Python/Flask 系の資料や fetcher は一部残っていますが、通常の画面と API は Spring Boot 側が中心です。

## 関連ドキュメント

- [docs/user_guide.md](docs/user_guide.md)
- [docs/spring_boot_refactor_plan.md](docs/spring_boot_refactor_plan.md)
- [CONTRIBUTING.md](CONTRIBUTING.md)
