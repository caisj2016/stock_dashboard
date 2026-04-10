package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CompanyProfileResponse", description = "Compact company profile summary used by the insights page.")
public record CompanyProfileResponse(
    @Schema(description = "Sector classification.", example = "Automobiles")
    String sector,
    @Schema(description = "Narrative track or strategic theme.", example = "EV and global auto exports")
    String track,
    @Schema(description = "Business description.", example = "Global automobile manufacturer with broad passenger and commercial vehicle lineup.")
    String business,
    @Schema(description = "Representative products or segments.", example = "Passenger cars, hybrids, commercial vehicles")
    String products
) {
}
