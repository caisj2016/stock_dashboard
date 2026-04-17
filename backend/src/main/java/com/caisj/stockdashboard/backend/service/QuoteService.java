package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.QuoteItemResponse;
import java.util.List;
import java.util.Map;

/**
 * 提供组合与指数行情查询能力。
 * 负责从本地持仓数据和外部行情源构建页面展示所需的行情响应。
 */
public interface QuoteService {
    /**
     * 获取当前组合中所有股票的行情数据，用于组合概览和持仓展示。
     */
    List<QuoteItemResponse> getPortfolioQuotes();

    /**
     * 获取预定义指数的实时行情，用于首页指数面板显示。
     */
    Map<String, com.caisj.stockdashboard.backend.dto.response.IndexQuoteResponse> getIndexQuotes();
}
