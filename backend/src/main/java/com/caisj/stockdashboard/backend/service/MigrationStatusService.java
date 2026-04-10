package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.MigrationStatusResponse;

public interface MigrationStatusService {
    MigrationStatusResponse getStatus();
}
