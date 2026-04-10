package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(name = "DashboardSnapshotResponse", description = "Aggregated home page payload with portfolio quotes and major index quotes.")
public record DashboardSnapshotResponse(
    @Schema(description = "Quote cards for the current portfolio or watchlist.")
    List<QuoteItemResponse> quotes,
    @Schema(description = "Major market indexes keyed by code or display key.")
    Map<String, IndexQuoteResponse> indexes,
    @Schema(description = "Timestamp string for the aggregated snapshot.", example = "2026-04-10 12:30 JST")
    String updated
) {
}
