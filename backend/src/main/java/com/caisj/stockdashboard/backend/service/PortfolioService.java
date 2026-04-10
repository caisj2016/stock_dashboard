package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.request.AddStockRequest;
import com.caisj.stockdashboard.backend.dto.request.RemoveStockRequest;
import com.caisj.stockdashboard.backend.dto.response.PortfolioResponse;
import java.util.List;

public interface PortfolioService {
    PortfolioResponse getPortfolio();

    PortfolioResponse updatePortfolio(List<PortfolioResponse.PortfolioItem> items);

    void addStock(AddStockRequest request);

    void removeStock(RemoveStockRequest request);
}
