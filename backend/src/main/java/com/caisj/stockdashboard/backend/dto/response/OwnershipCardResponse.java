package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "OwnershipCardResponse", description = "Single ownership summary card shown on the research page.")
public record OwnershipCardResponse(
    @Schema(description = "Stable card key used by the frontend.", example = "institutional")
    String key,
    @Schema(description = "Card title.", example = "Institutional Ownership")
    String title,
    @Schema(description = "Card subtitle or source note.", example = "Latest disclosed snapshot")
    String subtitle,
    @Schema(description = "Metrics rendered inside the card.")
    List<MetricItemResponse> items
) {
}
