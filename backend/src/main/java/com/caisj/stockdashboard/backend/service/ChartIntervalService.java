package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.domain.model.ChartIntervalConfig;

/**
 * 提供图表区间配置查询能力。
 * 负责根据前端请求的区间类型返回对应的历史数据拉取配置。
 */
public interface ChartIntervalService {
    /**
     * 获取指定图表区间的历史数据配置。
     */
    ChartIntervalConfig getConfig(String interval);
}
