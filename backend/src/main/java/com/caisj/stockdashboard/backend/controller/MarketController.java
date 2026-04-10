package com.caisj.stockdashboard.backend.controller;

import static com.caisj.stockdashboard.backend.api.OpenApiExamples.INTERNAL_ERROR;

import com.caisj.stockdashboard.backend.api.ApiResponse;
import com.caisj.stockdashboard.backend.dto.response.DashboardSnapshotResponse;
import com.caisj.stockdashboard.backend.dto.response.IndexQuoteResponse;
import com.caisj.stockdashboard.backend.dto.response.QuoteItemResponse;
import com.caisj.stockdashboard.backend.service.DashboardService;
import com.caisj.stockdashboard.backend.service.QuoteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Market", description = "Portfolio quotes, index quotes, and dashboard snapshot endpoints")
@RestController
@RequestMapping("/api")
public class MarketController {

    private final QuoteService quoteService;
    private final DashboardService dashboardService;

    public MarketController(QuoteService quoteService, DashboardService dashboardService) {
        this.quoteService = quoteService;
        this.dashboardService = dashboardService;
    }

    @Operation(summary = "Get portfolio quotes", description = "Returns quote cards for the current watchlist or portfolio items.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Quotes loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to load quotes",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/quotes")
    public ApiResponse<List<QuoteItemResponse>> getQuotes() {
        return ApiResponse.ok(quoteService.getPortfolioQuotes());
    }

    @Operation(summary = "Get index quotes", description = "Returns major market index quote data used by the dashboard header.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Index quotes loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to load index quotes",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/index_quotes")
    public ApiResponse<Map<String, IndexQuoteResponse>> getIndexQuotes() {
        return ApiResponse.ok(quoteService.getIndexQuotes());
    }

    @Operation(summary = "Get dashboard snapshot", description = "Returns the aggregated dashboard payload used by the home page.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Dashboard snapshot loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to load dashboard snapshot",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/dashboard_snapshot")
    public ApiResponse<DashboardSnapshotResponse> getDashboardSnapshot() {
        return ApiResponse.ok(dashboardService.getSnapshot());
    }
}
