package com.caisj.stockdashboard.backend.dto.response;

public record NewsItemResponse(
    String title,
    String titleEn,
    String summary,
    String pub,
    String provider,
    String url
) {
}
