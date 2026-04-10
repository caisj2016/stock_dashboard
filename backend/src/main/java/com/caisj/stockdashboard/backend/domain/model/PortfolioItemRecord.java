package com.caisj.stockdashboard.backend.domain.model;

public record PortfolioItemRecord(
    String code,
    String name,
    Double shares,
    Double cost,
    String status,
    String markerColor
) {
}
