package com.caisj.stockdashboard.backend.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RemoveStockRequest", description = "Request body used to remove a stock from the portfolio or watchlist.")
public record RemoveStockRequest(
    @Schema(description = "Ticker code or symbol to remove.", example = "7203.T")
    String code
) {
}
