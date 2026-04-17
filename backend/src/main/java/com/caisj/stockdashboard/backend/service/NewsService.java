package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.NewsItemResponse;
import java.util.List;

/**
 * 提供个股新闻抓取与本地化处理能力。
 * 负责将外部新闻源内容转换成前端显示的新闻项列表。
 */
public interface NewsService {
    /**
     * 获取指定股票的新闻列表。
     */
    List<NewsItemResponse> getStockNews(String symbol);
}
