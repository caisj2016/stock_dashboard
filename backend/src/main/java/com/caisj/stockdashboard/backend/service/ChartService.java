package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.ChartHistoryResponse;

public interface ChartService {
    ChartHistoryResponse getChartHistory(String symbol, String interval);
}
