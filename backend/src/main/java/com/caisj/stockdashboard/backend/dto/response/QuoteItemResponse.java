package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "QuoteItemResponse", description = "Quote card used by the dashboard and watchlist UI.")
public record QuoteItemResponse(
    @Schema(description = "Ticker symbol.", example = "7203.T")
    String symbol,
    @Schema(description = "Display name.", example = "Toyota Motor")
    String name,
    @Schema(description = "Latest price.", example = "2840.5")
    Double price,
    @Schema(description = "Previous close price.", example = "2805.7")
    Double prevClose,
    @Schema(description = "Absolute price change versus previous close.", example = "34.8")
    Double change,
    @Schema(description = "Percentage change versus previous close.", example = "1.24")
    Double pct,
    @Schema(description = "Latest trading volume.", example = "18200300")
    Long volume,
    @Schema(description = "Recent closing prices used for the sparkline.")
    List<Double> closes,
    @Schema(description = "Market state label such as REGULAR or CLOSED.", example = "REGULAR")
    String marketState,
    @Schema(description = "Timestamp string for the quote snapshot.", example = "2026-04-10 12:30 JST")
    String updated,
    @Schema(description = "Held shares for this symbol.", example = "100")
    Integer shares,
    @Schema(description = "Average cost basis per share.", example = "2750.0")
    Double cost,
    @Schema(description = "Optional watchlist or holding status.", example = "hold")
    String status,
    @Schema(description = "UI marker color.", example = "#ef4444")
    String markerColor,
    @Schema(description = "Unrealized profit and loss.", example = "9050.0")
    Double pnl,
    @Schema(description = "Unrealized profit and loss percentage.", example = "3.29")
    Double pnlPct,
    @Schema(description = "Current market value of the position.", example = "284050.0")
    Double marketValue,
    @Schema(description = "Cost value of the position.", example = "275000.0")
    Double costValue
) {
}
