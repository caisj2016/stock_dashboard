package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.config.AppProperties;
import com.caisj.stockdashboard.backend.dto.response.MigrationStatusResponse;
import com.caisj.stockdashboard.backend.service.MigrationStatusService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MigrationStatusServiceImpl implements MigrationStatusService {

    private final AppProperties appProperties;

    public MigrationStatusServiceImpl(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Override
    public MigrationStatusResponse getStatus() {
        return new MigrationStatusResponse(
            appProperties.getMigration().getSourcePythonFile(),
            "Spring Boot backend",
            List.of(
                new MigrationStatusResponse.ModuleStatus("project-skeleton", "done", "Spring Boot scaffold created"),
                new MigrationStatusResponse.ModuleStatus("screener", "done", "Spring Boot /api/screener is live"),
                new MigrationStatusResponse.ModuleStatus("chart-history", "done", "Spring Boot chart history and indicators are live"),
                new MigrationStatusResponse.ModuleStatus("portfolio", "done", "Portfolio read/write and dashboard snapshot are live"),
                new MigrationStatusResponse.ModuleStatus("news-and-ownership", "done", "Stock news, insights, ownership, digest, and macro feeds are available"),
                new MigrationStatusResponse.ModuleStatus("frontend-switch", "done", "Pages and APIs now run directly from Spring Boot"),
                new MigrationStatusResponse.ModuleStatus("python-shell-retired", "done", "The legacy Flask server.py entrypoint has been retired")
            )
        );
    }
}
