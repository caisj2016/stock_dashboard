package com.caisj.stockdashboard.backend.domain.model;

import java.util.List;

public record ScreenerMatch(
    boolean matched,
    int score,
    List<String> signals
) {
}
