package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.ScreenerResponse;

public interface ScreenerService {
    ScreenerResponse getScreener(String mode, String universe, int limit);
}
