package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "MetricItemResponse", description = "Single labeled metric shown inside research and ownership cards.")
public record MetricItemResponse(
    @Schema(description = "Metric label.", example = "P/E")
    String label,
    @Schema(description = "Formatted metric value for display.", example = "15.8x")
    String value,
    @Schema(description = "UI tone for the metric, such as positive, negative, neutral.", example = "neutral")
    String tone,
    @Schema(description = "Additional detail text shown under the metric.", nullable = true)
    String detail,
    @Schema(description = "Tooltip or helper copy explaining the metric.", nullable = true)
    String help,
    @Schema(description = "Raw numeric value when available.", example = "15.8")
    Double numeric
) {
}
