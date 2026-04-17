package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.domain.model.ScreenerUniverseDefinition;

/**
 * 提供选股池定义查询能力。
 * 负责返回预定义的股票池配置，供选股页面和筛选逻辑使用。
 */
public interface ScreenerUniverseService {
    /**
     * 获取指定 key 对应的股票池定义，默认返回核心池配置。
     */
    ScreenerUniverseDefinition getUniverse(String universeKey);
}
