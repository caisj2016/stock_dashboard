package com.caisj.stockdashboard.backend.service.impl;

import com.caisj.stockdashboard.backend.client.YahooFinanceClient;
import com.caisj.stockdashboard.backend.domain.model.PortfolioItemRecord;
import com.caisj.stockdashboard.backend.domain.model.QuoteSnapshot;
import com.caisj.stockdashboard.backend.dto.response.IndexQuoteResponse;
import com.caisj.stockdashboard.backend.dto.response.QuoteItemResponse;
import com.caisj.stockdashboard.backend.repository.PortfolioRepository;
import com.caisj.stockdashboard.backend.service.QuoteService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class QuoteServiceImpl implements QuoteService {

    private final PortfolioRepository portfolioRepository;
    private final YahooFinanceClient yahooFinanceClient;

    public QuoteServiceImpl(PortfolioRepository portfolioRepository, YahooFinanceClient yahooFinanceClient) {
        this.portfolioRepository = portfolioRepository;
        this.yahooFinanceClient = yahooFinanceClient;
    }

    @Override
    @Cacheable("quotes")
    public List<QuoteItemResponse> getPortfolioQuotes() {
        return portfolioRepository.findAll().stream()
            .map(this::toQuoteItem)
            .toList();
    }

    @Override
    @Cacheable("indexQuotes")
    public Map<String, IndexQuoteResponse> getIndexQuotes() {
        Map<String, String> symbols = Map.of("NI225", "^N225", "TOPIX", "1306.T");
        Map<String, IndexQuoteResponse> result = new LinkedHashMap<>();
        symbols.forEach((name, symbol) -> {
            QuoteSnapshot quote = yahooFinanceClient.fetchQuote(symbol);
            result.put(name, new IndexQuoteResponse(quote.price(), quote.change(), quote.pct()));
        });
        return result;
    }

    private QuoteItemResponse toQuoteItem(PortfolioItemRecord item) {
        QuoteSnapshot quote = yahooFinanceClient.fetchQuote(item.code());
        Integer shares = item.shares() == null ? 0 : item.shares().intValue();
        Double cost = item.cost() == null ? 0.0 : item.cost();
        Double pnl = null;
        Double pnlPct = null;
        Double marketValue = null;
        Double costValue = null;
        if ("holding".equals(item.status()) && quote.price() != null && shares > 0 && cost > 0) {
            marketValue = round(quote.price() * shares, 0);
            costValue = round(cost * shares, 0);
            pnl = round(marketValue - costValue, 0);
            pnlPct = costValue == 0 ? null : round((marketValue - costValue) / costValue * 100.0, 2);
        }

        return new QuoteItemResponse(
            item.code(),
            item.name(),
            quote.price(),
            quote.prevClose(),
            quote.change(),
            quote.pct(),
            quote.volume(),
            quote.closes(),
            quote.marketState(),
            quote.updated(),
            shares,
            cost,
            item.status(),
            item.markerColor(),
            pnl,
            pnlPct,
            marketValue,
            costValue
        );
    }

    private Double round(Double value, int scale) {
        if (value == null) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
