package com.caisj.stockdashboard.backend.api;

public final class OpenApiExamples {

    public static final String VALIDATION_ERROR = "{\"success\":false,\"code\":\"VALIDATION_ERROR\",\"message\":\"1 validation error(s)\",\"timestamp\":\"2026-04-10T13:50:00+09:00\",\"data\":null}";
    public static final String INTERNAL_ERROR = "{\"success\":false,\"code\":\"INTERNAL_ERROR\",\"message\":\"Upstream request failed\",\"timestamp\":\"2026-04-10T13:50:00+09:00\",\"data\":null}";
    public static final String INVALID_SYMBOL = "{\"success\":false,\"code\":\"INVALID_SYMBOL\",\"message\":\"Ticker symbol is required\",\"timestamp\":\"2026-04-10T13:50:00+09:00\",\"data\":null}";
    public static final String INVALID_TOPIC = "{\"success\":false,\"code\":\"INVALID_TOPIC\",\"message\":\"Topic is required\",\"timestamp\":\"2026-04-10T13:50:00+09:00\",\"data\":null}";

    private OpenApiExamples() {
    }
}
