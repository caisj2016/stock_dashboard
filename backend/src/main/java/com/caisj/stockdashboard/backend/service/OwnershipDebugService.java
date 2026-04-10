package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.OwnershipShortDebugResponse;

public interface OwnershipDebugService {
    OwnershipShortDebugResponse getOwnershipShortDebug(String symbol);
}
