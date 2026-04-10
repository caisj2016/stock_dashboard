package com.caisj.stockdashboard.backend.dto.response;

import java.util.List;

public record TrumpNewsItemResponse(
    String source,
    String pub,
    String title,
    String titleZh,
    String summaryZh,
    String brief,
    String url,
    List<String> marketTags
) {
}
