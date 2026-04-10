package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "OwnershipShortResponse", description = "Ownership concentration and short-interest summary for a stock symbol.")
public record OwnershipShortResponse(
    @Schema(description = "Whether ownership data was loaded successfully.", example = "true")
    boolean ok,
    @Schema(description = "Ticker symbol requested by the page.", example = "7203.T")
    String symbol,
    @Schema(description = "Page title or stock label returned by the data source.", example = "Toyota Motor")
    String title,
    @Schema(description = "Timestamp string for the summary payload.", example = "2026-04-10 12:30 JST")
    String updated,
    @Schema(description = "Error message when data extraction fails.", nullable = true)
    String error,
    @Schema(description = "Ownership summary cards rendered by the page.")
    List<OwnershipCardResponse> cards
) {
}
