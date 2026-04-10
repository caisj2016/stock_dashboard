package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "TopicDigestResponse", description = "Curated topic digest with tone, drivers, summary text, and selected items.")
public record TopicDigestResponse(
    @Schema(description = "Digest topic key.", example = "nikkei")
    String topic,
    @Schema(description = "Overall topic tone shown to users.", example = "偏多")
    String tone,
    @Schema(description = "CSS-friendly tone class used by the frontend.", example = "up")
    String toneClass,
    @Schema(description = "Narrative summary for the digest.", example = "日本市场当前基调为偏多，主线聚焦日本宏观与 AI 资本开支。")
    String summary,
    @Schema(description = "Timestamp string for the digest payload.", example = "16:20")
    String updated,
    @Schema(description = "Key market drivers extracted for the digest.")
    List<String> drivers,
    @Schema(description = "Curated news items included in the digest.")
    List<TopicDigestItemResponse> items
) {
}
