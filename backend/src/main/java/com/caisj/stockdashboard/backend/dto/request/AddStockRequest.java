package com.caisj.stockdashboard.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AddStockRequest", description = "Request body used to add a stock to the portfolio or watchlist.")
public record AddStockRequest(
    @Schema(description = "Ticker code or symbol to add.", example = "7203.T")
    String code,
    @Schema(description = "Optional display name stored with the symbol.", example = "Toyota Motor")
    String name
) {
}
