package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "IndexQuoteResponse", description = "Simplified market index quote used by the dashboard header.")
public record IndexQuoteResponse(
    @Schema(description = "Latest index level.", example = "39850.12")
    Double price,
    @Schema(description = "Absolute change versus previous close.", example = "215.45")
    Double change,
    @Schema(description = "Percentage change versus previous close.", example = "0.54")
    Double pct
) {
}
