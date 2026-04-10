package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.StockInsightsResponse;

public interface InsightsService {
    StockInsightsResponse getStockInsights(String symbol);
}
