package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.StockInsightsResponse;

/**
 * 提供个股洞察结果查询能力。
 * 负责综合历史行情与基本面数据，构建股票洞察卡片数据。
 */
public interface InsightsService {
    /**
     * 获取指定股票的可视化洞察信息。
     */
    StockInsightsResponse getStockInsights(String symbol);
}
