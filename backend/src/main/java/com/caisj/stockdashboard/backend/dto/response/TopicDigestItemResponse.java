package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TopicDigestItemResponse", description = "Single curated news item included in a topic digest.")
public record TopicDigestItemResponse(
    @Schema(description = "Source provider name.", example = "Reuters")
    String provider,
    @Schema(description = "Publication time text shown in the digest.", example = "04-10 14:00")
    String pub,
    @Schema(description = "Original headline.", example = "Nikkei rises on chip shares")
    String title,
    @Schema(description = "Chinese translated headline, if available.", example = "日经指数在芯片股带动下上涨")
    String titleZh,
    @Schema(description = "Short summary used by the digest page.", example = "AI 投资与芯片周期仍是板块主线。")
    String brief,
    @Schema(description = "Original article URL.", example = "https://www.reuters.com/example")
    String url
) {
}
