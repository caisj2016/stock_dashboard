package com.caisj.stockdashboard.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.caisj.stockdashboard.backend.client.YahooFinanceClient;
import com.caisj.stockdashboard.backend.dto.response.ChartHistoryResponse;
import com.caisj.stockdashboard.backend.dto.response.StockInsightsResponse;
import com.caisj.stockdashboard.backend.service.impl.InsightsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class InsightsServiceImplTests {

    private final ChartService chartService = mock(ChartService.class);
    private final YahooFinanceClient yahooFinanceClient = mock(YahooFinanceClient.class);
    private final InsightsServiceImpl insightsService = new InsightsServiceImpl(chartService, yahooFinanceClient);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldUseAssetProfileAndBuildReadableFallbackProfile() {
        when(chartService.getChartHistory("7203.T", "D")).thenReturn(sampleChart());
        when(yahooFinanceClient.fetchQuoteSummary(eq("7203.T"), anyList())).thenReturn(sampleQuoteSummary());

        StockInsightsResponse response = insightsService.getStockInsights("7203.T");

        assertThat(response.ok()).isTrue();
        assertThat(response.profile().sector()).isEqualTo("Consumer Cyclical");
        assertThat(response.profile().track()).isEqualTo("Auto Manufacturers");
        assertThat(response.profile().business()).contains("Toyota Motor Corp");
        assertThat(response.profile().products()).contains("Core focus");
        assertThat(response.profile().products()).contains("Auto Manufacturers");
    }

    @Test
    void shouldReturnProfileFallbackWhenQuoteSummaryUnavailable() {
        when(chartService.getChartHistory("6758.T", "D")).thenReturn(sampleChart());
        when(yahooFinanceClient.fetchQuoteSummary(eq("6758.T"), anyList())).thenReturn(objectMapper.createObjectNode());

        StockInsightsResponse response = insightsService.getStockInsights("6758.T");

        assertThat(response.ok()).isTrue();
        assertThat(response.profile().business()).isEqualTo("Profile unavailable.");
        assertThat(response.profile().products()).isEqualTo("Profile unavailable.");
    }

    private ChartHistoryResponse sampleChart() {
        return new ChartHistoryResponse(
            true,
            "7203.T",
            "Toyota Motor Corp",
            "D",
            "Daily",
            null,
            List.of("2026-04-08", "2026-04-09", "2026-04-10"),
            List.of(1L, 2L, 3L),
            List.of(2800.0, 2820.0, 2840.0),
            List.of(2810.0, 2830.0, 2850.0),
            List.of(2790.0, 2810.0, 2830.0),
            List.of(2805.0, 2825.0, 2845.0),
            List.of(1_000_000L, 1_100_000L, 1_200_000L),
            List.of(2800.0, 2810.0, 2820.0),
            List.of(2795.0, 2805.0, 2815.0),
            List.of(1.0, 1.1, 1.2),
            List.of(0.8, 0.9, 1.0),
            List.of(0.2, 0.2, 0.2),
            2845.0,
            0.75,
            54.3,
            1.18,
            "15:30"
        );
    }

    private ObjectNode sampleQuoteSummary() {
        ObjectNode root = objectMapper.createObjectNode();

        ObjectNode price = root.putObject("price");
        price.put("longName", "Toyota Motor Corp");
        price.put("exchangeName", "Tokyo");
        price.put("quoteType", "EQUITY");

        ObjectNode assetProfile = root.putObject("assetProfile");
        assetProfile.put("sector", "Consumer Cyclical");
        assetProfile.put("industry", "Auto Manufacturers");
        assetProfile.put("country", "Japan");

        ObjectNode financialData = root.putObject("financialData");
        financialData.putObject("targetMeanPrice").put("raw", 3100.0);
        financialData.putObject("numberOfAnalystOpinions").put("raw", 18.0);

        ObjectNode stats = root.putObject("defaultKeyStatistics");
        stats.putObject("forwardPE").put("raw", 9.5);

        return root;
    }
}
