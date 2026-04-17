package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.domain.model.ChartIntervalConfig;
import com.caisj.stockdashboard.backend.service.ChartIntervalService;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 图表区间配置服务实现类。
 * 负责提供不同图表周期对应的历史行情拉取参数。
 */
@Service
public class ChartIntervalServiceImpl implements ChartIntervalService {

    private static final Map<String, ChartIntervalConfig> CONFIGS = Map.of(
        "D", new ChartIntervalConfig("D", "6mo", "1d", "日线", 90, null),
        "W", new ChartIntervalConfig("W", "2y", "1wk", "周线", 90, null),
        "M", new ChartIntervalConfig("M", "5y", "1mo", "月线", 90, null),
        "60", new ChartIntervalConfig("60", "60d", "60m", "1小时", 80, null),
        "15", new ChartIntervalConfig("15", "30d", "15m", "15分钟", 80, null),
        "240", new ChartIntervalConfig("240", "60d", "60m", "4小时", 80, 4)
    );

    /**
     * 返回指定区间的图表配置。
     * 如果输入无效，则默认返回日线配置。
     */
    @Override
    public ChartIntervalConfig getConfig(String interval) {
        String normalized = interval == null ? "D" : interval.trim().toUpperCase();
        return CONFIGS.getOrDefault(normalized, CONFIGS.get("D"));
    }
}
