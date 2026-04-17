package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.DashboardSnapshotResponse;

/**
 * 提供首页仪表盘快照数据的服务接口。
 * 主要用于获取组合行情与指数行情的展示数据。
 */
public interface DashboardService {
    /**
     * 获取当前仪表盘快照，包括组合行情、指数行情和当前时间。
     */
    DashboardSnapshotResponse getSnapshot();
}
