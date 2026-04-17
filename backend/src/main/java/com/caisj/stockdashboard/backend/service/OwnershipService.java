package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.OwnershipShortResponse;

/**
 * 提供个股机构和空头持仓概览数据。
 * 负责生成前端持仓结构与空头压力展示所需的摘要信息。
 */
public interface OwnershipService {
    /**
     * 获取指定股票的机构与空头持仓摘要。
     */
    OwnershipShortResponse getOwnershipShort(String symbol);
}
