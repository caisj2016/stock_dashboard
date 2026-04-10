package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "InsightGroupResponse", description = "Logical group of research metrics displayed on the insights page.")
public record InsightGroupResponse(
    @Schema(description = "Group title.", example = "Valuation")
    String title,
    @Schema(description = "Optional subtitle providing context for the group.", example = "Latest available market metrics")
    String subtitle,
    @Schema(description = "Metrics included in the group.")
    List<MetricItemResponse> items
) {
}
