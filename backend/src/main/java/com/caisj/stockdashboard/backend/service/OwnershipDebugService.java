package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.OwnershipShortDebugResponse;

/**
 * 提供机构与空头持仓的调试级别数据。
 * 负责返回更详细的原始抓取信息和解析结果，便于调试与问题定位。
 */
public interface OwnershipDebugService {
    /**
     * 获取指定股票的机构和空头持仓调试信息。
     */
    OwnershipShortDebugResponse getOwnershipShortDebug(String symbol);
}
