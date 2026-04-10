package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "PortfolioResponse", description = "Current stored portfolio or watchlist items.")
public record PortfolioResponse(
    @Schema(description = "Portfolio items persisted by the backend.")
    List<PortfolioItem> items
) {

    @Schema(name = "PortfolioItem", description = "Single portfolio or watchlist item.")
    public record PortfolioItem(
        @Schema(description = "Ticker symbol.", example = "7203.T")
        String symbol,
        @Schema(description = "Display name shown in the UI.", example = "Toyota Motor")
        String name,
        @Schema(description = "Held shares. May be null for watchlist-only entries.", example = "100")
        Integer shares,
        @Schema(description = "Average cost basis per share.", example = "2750.0")
        Double cost,
        @Schema(description = "Optional position status label.", example = "watch")
        String status,
        @Schema(description = "Marker color used by the chart or list UI.", example = "#ef4444")
        String markerColor
    ) {
    }
}
