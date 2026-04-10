package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.domain.model.ScreenerUniverseDefinition;

public interface ScreenerUniverseService {
    ScreenerUniverseDefinition getUniverse(String universeKey);
}
