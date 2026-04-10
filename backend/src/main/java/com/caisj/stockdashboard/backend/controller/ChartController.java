package com.caisj.stockdashboard.backend.controller;

import static com.caisj.stockdashboard.backend.api.OpenApiExamples.INTERNAL_ERROR;
import static com.caisj.stockdashboard.backend.api.OpenApiExamples.VALIDATION_ERROR;

import com.caisj.stockdashboard.backend.api.ApiResponse;
import com.caisj.stockdashboard.backend.dto.response.ChartHistoryResponse;
import com.caisj.stockdashboard.backend.service.ChartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Charts", description = "Chart history and technical indicator endpoints")
@RestController
@RequestMapping("/api")
public class ChartController {

    private final ChartService chartService;

    public ChartController(ChartService chartService) {
        this.chartService = chartService;
    }

    @Operation(
        summary = "Get chart history",
        description = "Returns chart-ready OHLC, volume, moving averages, MACD, RSI, and summary fields for a stock symbol."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chart history loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Validation error", value = VALIDATION_ERROR)
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to load chart history",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/chart-history")
    public ApiResponse<ChartHistoryResponse> getChartHistory(
        @Parameter(description = "Ticker symbol, for example 7203.T or AAPL", example = "7203.T")
        @RequestParam String symbol,
        @Parameter(description = "Chart interval code used by the page, such as D, W, M, 1H", example = "D")
        @RequestParam(defaultValue = "D") String interval
    ) {
        return ApiResponse.ok(chartService.getChartHistory(symbol, interval));
    }

    @Operation(
        summary = "Get chart history (compatibility path)",
        description = "Legacy compatibility endpoint kept for older frontend callers. Response shape matches the main chart history endpoint."
    )
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Chart history loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request parameters",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Validation error", value = VALIDATION_ERROR)
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to load chart history",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/chart_history")
    public ApiResponse<ChartHistoryResponse> getChartHistoryCompat(
        @Parameter(description = "Ticker symbol, for example 7203.T or AAPL", example = "7203.T")
        @RequestParam String symbol,
        @Parameter(description = "Chart interval code used by the page, such as D, W, M, 1H", example = "D")
        @RequestParam(defaultValue = "D") String interval
    ) {
        return ApiResponse.ok(chartService.getChartHistory(symbol, interval));
    }
}
