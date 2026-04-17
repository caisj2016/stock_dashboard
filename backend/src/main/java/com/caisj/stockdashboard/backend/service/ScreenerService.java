package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.ScreenerResponse;

/**
 * 提供选股结果查询能力。
 * 负责根据筛选模式和股票池构建可展示的选股列表。
 */
public interface ScreenerService {
    /**
     * 获取筛选结果页面需要的选股数据。
     */
    ScreenerResponse getScreener(String mode, String universe, int limit);
}
