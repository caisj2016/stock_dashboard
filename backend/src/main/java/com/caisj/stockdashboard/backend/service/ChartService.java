package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.ChartHistoryResponse;

/**
 * 提供历史行情图表数据的服务接口。
 * 负责组装图表页面所需的 OHLC、指标和时间序列数据。
 */
public interface ChartService {
    /**
     * 获取指定股票和区间的图表历史数据。
     */
    ChartHistoryResponse getChartHistory(String symbol, String interval);
}
