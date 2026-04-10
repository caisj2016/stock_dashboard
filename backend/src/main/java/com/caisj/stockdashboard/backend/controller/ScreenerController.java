package com.caisj.stockdashboard.backend.controller;

import static com.caisj.stockdashboard.backend.api.OpenApiExamples.INTERNAL_ERROR;
import static com.caisj.stockdashboard.backend.api.OpenApiExamples.VALIDATION_ERROR;

import com.caisj.stockdashboard.backend.api.ApiResponse;
import com.caisj.stockdashboard.backend.dto.response.ScreenerResponse;
import com.caisj.stockdashboard.backend.service.ScreenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "Screener", description = "Stock screener endpoints")
@RestController
@RequestMapping("/api")
public class ScreenerController {

    private final ScreenerService screenerService;

    public ScreenerController(ScreenerService screenerService) {
        this.screenerService = screenerService;
    }

    @Operation(summary = "Run screener", description = "Returns screener results for a mode, universe, and result limit.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Screener results loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid screener parameters",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Validation error", value = VALIDATION_ERROR)
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to run screener",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/screener")
    public ApiResponse<ScreenerResponse> getScreener(
        @Parameter(description = "Screener mode, for example combo, oversold, breakout", example = "combo")
        @RequestParam(defaultValue = "combo") String mode,
        @Parameter(description = "Stock universe key, for example core45, nikkei225, topixcore", example = "core45")
        @RequestParam(defaultValue = "core45") String universe,
        @Parameter(description = "Maximum number of result cards to return", example = "18")
        @RequestParam(defaultValue = "18") @Min(1) @Max(100) int limit
    ) {
        return ApiResponse.ok(screenerService.getScreener(mode, universe, limit));
    }
}
