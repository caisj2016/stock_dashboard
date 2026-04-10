@echo off
cd /d "%~dp0"
java -jar "backend\target\stock-dashboard-backend-0.0.1-SNAPSHOT.jar" --server.port=18080 --app.portfolio.file="portfolio.json" --app.portfolio.backup-dir="data_backups"
