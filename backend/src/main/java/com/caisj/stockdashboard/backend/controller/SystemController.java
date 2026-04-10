package com.caisj.stockdashboard.backend.controller;

import static com.caisj.stockdashboard.backend.api.OpenApiExamples.INTERNAL_ERROR;

import com.caisj.stockdashboard.backend.api.ApiResponse;
import com.caisj.stockdashboard.backend.dto.response.MigrationStatusResponse;
import com.caisj.stockdashboard.backend.service.MigrationStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "System", description = "Health and migration status endpoints")
@RestController
@RequestMapping("/api")
public class SystemController {

    private final MigrationStatusService migrationStatusService;

    public SystemController(MigrationStatusService migrationStatusService) {
        this.migrationStatusService = migrationStatusService;
    }

    @Operation(summary = "Health check", description = "Simple health endpoint for process liveness and basic smoke checks.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Service is healthy"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Service is unhealthy",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/healthz")
    public ApiResponse<Map<String, String>> health() {
        return ApiResponse.ok(Map.of("status", "ok", "service", "stock-dashboard-backend"));
    }

    @Operation(summary = "Migration status", description = "Returns the current migration status and backend ownership of major modules.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Migration status loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to load migration status",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/migration/status")
    public ApiResponse<MigrationStatusResponse> migrationStatus() {
        return ApiResponse.ok(migrationStatusService.getStatus());
    }
}
