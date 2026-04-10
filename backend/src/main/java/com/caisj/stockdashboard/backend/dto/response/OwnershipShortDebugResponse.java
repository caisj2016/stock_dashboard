package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(name = "OwnershipShortDebugResponse", description = "Diagnostic payload used to troubleshoot ownership and short-interest parsing.")
public record OwnershipShortDebugResponse(
    @Schema(description = "Whether the debug payload was built successfully.", example = "true")
    boolean ok,
    @Schema(description = "Ticker symbol requested for debug.", example = "7203.T")
    String symbol,
    @Schema(description = "Detected market or venue name.", example = "TSE")
    String market,
    @Schema(description = "Timestamp when the debug request was generated.", example = "2026-04-10T12:30:00+09:00")
    String requestedAt,
    @Schema(description = "Upstream source metadata and fetch information.")
    Map<String, Object> source,
    @Schema(description = "Raw extracted rows and parser artifacts.")
    Map<String, Object> raw,
    @Schema(description = "Derived fields calculated from the raw extraction.")
    Map<String, Object> derived,
    @Schema(description = "Short diagnosis string summarizing parser outcome.", example = "institutional data available")
    String diagnosis,
    @Schema(description = "Field names that were expected but missing from the parse result.")
    List<String> missingFields,
    @Schema(description = "Final payload candidate produced from the debug run.")
    Object finalPayload
) {
}
