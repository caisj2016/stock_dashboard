package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.dto.response.QuoteItemResponse;
import java.util.List;
import java.util.Map;

public interface QuoteService {
    List<QuoteItemResponse> getPortfolioQuotes();

    Map<String, com.caisj.stockdashboard.backend.dto.response.IndexQuoteResponse> getIndexQuotes();
}
