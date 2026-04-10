package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.OwnershipShortResponse;

public interface OwnershipService {
    OwnershipShortResponse getOwnershipShort(String symbol);
}
