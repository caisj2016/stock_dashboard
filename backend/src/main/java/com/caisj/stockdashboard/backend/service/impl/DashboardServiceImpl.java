package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.dto.response.DashboardSnapshotResponse;
import com.caisj.stockdashboard.backend.service.DashboardService;
import com.caisj.stockdashboard.backend.service.QuoteService;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * DashboardService 的实现类。
 * 通过行情服务获取组合与指数数据，并缓存仪表盘快照结果。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final QuoteService quoteService;

    public DashboardServiceImpl(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

    /**
     * 调用 QuoteService 获取组合和指数行情，并附加当前时间，返回首页展示所需快照数据。
     */
    @Override
    @Cacheable("dashboardSnapshot")
    public DashboardSnapshotResponse getSnapshot() {
        return new DashboardSnapshotResponse(
            quoteService.getPortfolioQuotes(),
            quoteService.getIndexQuotes(),
            LocalTime.now().format(TIME_FORMATTER)
        );
    }
}
