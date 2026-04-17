package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.MigrationStatusResponse;

/**
 * 提供项目迁移状态查询能力。
 * 负责返回当前后端迁移进度与模块状态摘要。
 */
public interface MigrationStatusService {
    /**
     * 获取当前迁移状态信息。
     */
    MigrationStatusResponse getStatus();
}
