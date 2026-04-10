package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.domain.model.ChartIntervalConfig;

public interface ChartIntervalService {
    ChartIntervalConfig getConfig(String interval);
}
