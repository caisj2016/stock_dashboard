package com.caisj.stockdashboard.backend.domain.model;

import java.util.List;

public record ScreenerUniverseDefinition(
    String key,
    String label,
    String description,
    List<ScreenerCandidate> items
) {
}
