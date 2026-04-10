package com.caisj.stockdashboard.backend.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(name = "ApiResponse", description = "Unified API response envelope used by all backend endpoints.")
public record ApiResponse<T>(
    @Schema(description = "Whether the request completed successfully.", example = "true")
    boolean success,
    @Schema(description = "Application-level response code.", example = "OK")
    String code,
    @Schema(description = "Human-readable error or status message. Usually null on success.", nullable = true, example = "Invalid symbol")
    String message,
    @Schema(description = "Server timestamp when the response was generated.", example = "2026-04-10T13:42:00+09:00")
    OffsetDateTime timestamp,
    @Schema(description = "Actual response payload. Null when the request fails.")
    T data
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", null, OffsetDateTime.now(), data);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code, message, OffsetDateTime.now(), null);
    }
}
