@echo off
cd /d "%~dp0\backend"
set "APP_PORTFOLIO_FILE=%~dp0portfolio.json"
set "APP_PORTFOLIO_BACKUP_DIR=%~dp0data_backups"
set "SPRING_PROFILES_ACTIVE=dev"
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18080
