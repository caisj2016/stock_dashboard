package com.caisj.stockdashboard.backend.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Cors cors = new Cors();
    private final Portfolio portfolio = new Portfolio();
    private final Migration migration = new Migration();
    private final MarketData marketData = new MarketData();

    public Cors getCors() {
        return cors;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public Migration getMigration() {
        return migration;
    }

    public MarketData getMarketData() {
        return marketData;
    }

    public static class Cors {
        private List<String> allowedOrigins = new ArrayList<>();

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    public static class Portfolio {
        private String file;
        private String backupDir;
        private int backupLimit = 20;

        public String getFile() {
            return file;
        }

        public void setFile(String file) {
            this.file = file;
        }

        public String getBackupDir() {
            return backupDir;
        }

        public void setBackupDir(String backupDir) {
            this.backupDir = backupDir;
        }

        public int getBackupLimit() {
            return backupLimit;
        }

        public void setBackupLimit(int backupLimit) {
            this.backupLimit = backupLimit;
        }
    }

    public static class Migration {
        private String sourcePythonFile;

        public String getSourcePythonFile() {
            return sourcePythonFile;
        }

        public void setSourcePythonFile(String sourcePythonFile) {
            this.sourcePythonFile = sourcePythonFile;
        }
    }

    public static class MarketData {
        private String yahooChartBaseUrl;
        private int timeoutSeconds = 12;

        public String getYahooChartBaseUrl() {
            return yahooChartBaseUrl;
        }

        public void setYahooChartBaseUrl(String yahooChartBaseUrl) {
            this.yahooChartBaseUrl = yahooChartBaseUrl;
        }

        public int getTimeoutSeconds() {
            return timeoutSeconds;
        }

        public void setTimeoutSeconds(int timeoutSeconds) {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
