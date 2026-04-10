package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.DashboardSnapshotResponse;

public interface DashboardService {
    DashboardSnapshotResponse getSnapshot();
}
