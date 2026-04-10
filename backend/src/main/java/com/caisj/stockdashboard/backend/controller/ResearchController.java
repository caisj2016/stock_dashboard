package com.caisj.stockdashboard.backend.controller;

import static com.caisj.stockdashboard.backend.api.OpenApiExamples.INTERNAL_ERROR;
import static com.caisj.stockdashboard.backend.api.OpenApiExamples.INVALID_SYMBOL;
import static com.caisj.stockdashboard.backend.api.OpenApiExamples.INVALID_TOPIC;

import com.caisj.stockdashboard.backend.api.ApiResponse;
import com.caisj.stockdashboard.backend.dto.response.NewsItemResponse;
import com.caisj.stockdashboard.backend.dto.response.OwnershipShortDebugResponse;
import com.caisj.stockdashboard.backend.dto.response.OwnershipShortResponse;
import com.caisj.stockdashboard.backend.dto.response.StockInsightsResponse;
import com.caisj.stockdashboard.backend.dto.response.TopicDigestResponse;
import com.caisj.stockdashboard.backend.dto.response.TrumpNewsItemResponse;
import com.caisj.stockdashboard.backend.service.InsightsService;
import com.caisj.stockdashboard.backend.service.MacroNewsService;
import com.caisj.stockdashboard.backend.service.NewsService;
import com.caisj.stockdashboard.backend.service.OwnershipDebugService;
import com.caisj.stockdashboard.backend.service.OwnershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Research", description = "News, insights, ownership, and digest endpoints")
@RestController
@RequestMapping("/api")
public class ResearchController {

    private final NewsService newsService;
    private final InsightsService insightsService;
    private final OwnershipService ownershipService;
    private final OwnershipDebugService ownershipDebugService;
    private final MacroNewsService macroNewsService;

    public ResearchController(
        NewsService newsService,
        InsightsService insightsService,
        OwnershipService ownershipService,
        OwnershipDebugService ownershipDebugService,
        MacroNewsService macroNewsService
    ) {
        this.newsService = newsService;
        this.insightsService = insightsService;
        this.ownershipService = ownershipService;
        this.ownershipDebugService = ownershipDebugService;
        this.macroNewsService = macroNewsService;
    }

    @Operation(summary = "Get stock news", description = "Returns normalized news items for the requested stock symbol.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stock news loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid symbol",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Invalid symbol", value = INVALID_SYMBOL)
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to load stock news",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/stock_news")
    public ApiResponse<List<NewsItemResponse>> getStockNews(
        @Parameter(description = "Ticker symbol, for example 7203.T or AAPL", example = "7203.T")
        @RequestParam String symbol
    ) {
        return ApiResponse.ok(newsService.getStockNews(symbol));
    }

    @Operation(summary = "Get stock insights", description = "Returns business profile, derived metrics, and summary insights for a stock symbol.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stock insights loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid symbol",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Invalid symbol", value = INVALID_SYMBOL)
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to load stock insights",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/stock_insights")
    public ApiResponse<StockInsightsResponse> getStockInsights(
        @Parameter(description = "Ticker symbol, for example 7203.T or AAPL", example = "7203.T")
        @RequestParam String symbol
    ) {
        return ApiResponse.ok(insightsService.getStockInsights(symbol));
    }

    @Operation(summary = "Get ownership and short-interest summary", description = "Returns ownership concentration, short-interest, and source coverage information.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ownership summary loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid symbol",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Invalid symbol", value = INVALID_SYMBOL)
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to load ownership summary",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/ownership_short")
    public ApiResponse<OwnershipShortResponse> getOwnershipShort(
        @Parameter(description = "Ticker symbol, for example 7203.T or 9509.T", example = "7203.T")
        @RequestParam String symbol
    ) {
        return ApiResponse.ok(ownershipService.getOwnershipShort(symbol));
    }

    @Operation(summary = "Get ownership debug payload", description = "Returns raw parsing and diagnostic fields for troubleshooting ownership and short-interest extraction.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Ownership debug payload loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid symbol",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Invalid symbol", value = INVALID_SYMBOL)
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to load ownership debug payload",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/ownership_short_debug")
    public ApiResponse<OwnershipShortDebugResponse> getOwnershipShortDebug(
        @Parameter(description = "Ticker symbol, for example 7203.T or 9509.T", example = "7203.T")
        @RequestParam String symbol
    ) {
        return ApiResponse.ok(ownershipDebugService.getOwnershipShortDebug(symbol));
    }

    @Operation(summary = "Get Trump-related macro news", description = "Returns curated macro-sensitive news items tracked for Trump and related policy headlines.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Macro news loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to load macro news",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/trump_news")
    public ApiResponse<List<TrumpNewsItemResponse>> getTrumpNews() {
        return ApiResponse.ok(macroNewsService.getTrumpNews());
    }

    @Operation(summary = "Get topic digest", description = "Returns a compact topic digest with tone, drivers, and curated news items.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Topic digest loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid topic",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Invalid topic", value = INVALID_TOPIC)
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to load topic digest",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/topic_digest")
    public ApiResponse<TopicDigestResponse> getTopicDigest(
        @Parameter(description = "Digest topic key, for example nikkei or semiconductor", example = "nikkei")
        @RequestParam String topic
    ) {
        return ApiResponse.ok(macroNewsService.getTopicDigest(topic));
    }
}
