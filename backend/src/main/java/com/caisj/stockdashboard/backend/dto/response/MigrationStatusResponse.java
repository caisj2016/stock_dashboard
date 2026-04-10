package com.caisj.stockdashboard.backend.dto.response;

import java.util.List;

public record MigrationStatusResponse(
    String sourcePythonFile,
    String targetModule,
    List<ModuleStatus> modules
) {

    public record ModuleStatus(
        String key,
        String status,
        String notes
    ) {
    }
}
