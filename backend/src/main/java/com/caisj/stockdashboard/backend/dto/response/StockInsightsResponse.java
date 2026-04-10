package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "StockInsightsResponse", description = "Stock profile and grouped insight metrics for the research page.")
public record StockInsightsResponse(
    @Schema(description = "Whether the insights payload was built successfully.", example = "true")
    boolean ok,
    @Schema(description = "Ticker symbol requested by the page.", example = "7203.T")
    String symbol,
    @Schema(description = "Timestamp string for the insights payload.", example = "2026-04-10 12:30 JST")
    String updated,
    @Schema(description = "Error message when insight loading fails.", nullable = true)
    String error,
    @Schema(description = "Company profile summary.")
    CompanyProfileResponse profile,
    @Schema(description = "Grouped insight cards for rendering.")
    List<InsightGroupResponse> groups
) {
}
