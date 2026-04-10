package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.dto.response.DashboardSnapshotResponse;
import com.caisj.stockdashboard.backend.service.DashboardService;
import com.caisj.stockdashboard.backend.service.QuoteService;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final QuoteService quoteService;

    public DashboardServiceImpl(QuoteService quoteService) {
        this.quoteService = quoteService;
    }

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
