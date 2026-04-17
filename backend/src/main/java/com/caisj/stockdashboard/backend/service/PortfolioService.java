package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.request.AddStockRequest;
import com.caisj.stockdashboard.backend.dto.request.RemoveStockRequest;
import com.caisj.stockdashboard.backend.dto.response.PortfolioResponse;
import java.util.List;

/**
 * 管理用户投资组合数据的服务接口。
 * 定义组合读取、批量更新和单条股票增删的业务能力。
 */
public interface PortfolioService {
    /**
     * 获取当前组合持仓列表及其基础信息。
     */
    PortfolioResponse getPortfolio();

    /**
     * 更新组合数据为新传入的持仓列表，并返回保存后的组合结果。
     */
    PortfolioResponse updatePortfolio(List<PortfolioResponse.PortfolioItem> items);

    /**
     * 新增一只股票到组合中，通常用于用户添加关注或持仓记录。
     */
    void addStock(AddStockRequest request);

    /**
     * 从组合中删除指定股票，适用于用户移除关注或清仓操作。
     */
    void removeStock(RemoveStockRequest request);
}
