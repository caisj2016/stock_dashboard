package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.NewsItemResponse;
import java.util.List;

public interface NewsService {
    List<NewsItemResponse> getStockNews(String symbol);
}
