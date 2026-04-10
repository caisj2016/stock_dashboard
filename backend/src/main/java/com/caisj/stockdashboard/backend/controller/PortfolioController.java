package com.caisj.stockdashboard.backend.controller;

import static com.caisj.stockdashboard.backend.api.OpenApiExamples.INTERNAL_ERROR;
import static com.caisj.stockdashboard.backend.api.OpenApiExamples.VALIDATION_ERROR;

import com.caisj.stockdashboard.backend.api.ApiResponse;
import com.caisj.stockdashboard.backend.dto.request.AddStockRequest;
import com.caisj.stockdashboard.backend.dto.request.RemoveStockRequest;
import com.caisj.stockdashboard.backend.dto.response.PortfolioResponse;
import com.caisj.stockdashboard.backend.service.PortfolioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Portfolio", description = "Portfolio and watchlist management endpoints")
@RestController
@RequestMapping("/api")
public class PortfolioController {

    private final PortfolioService portfolioService;

    public PortfolioController(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @Operation(summary = "Get portfolio", description = "Returns the current watchlist or portfolio items used by the dashboard.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Portfolio loaded successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to load portfolio",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @GetMapping("/portfolio")
    public ApiResponse<PortfolioResponse> getPortfolio() {
        return ApiResponse.ok(portfolioService.getPortfolio());
    }

    @Operation(summary = "Replace portfolio items", description = "Replaces the stored portfolio items with the submitted list.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Portfolio updated successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request body",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Validation error", value = VALIDATION_ERROR)
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to update portfolio",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
        required = true,
        description = "Complete portfolio item list to persist",
        content = @Content(array = @ArraySchema(schema = @Schema(implementation = PortfolioResponse.PortfolioItem.class)))
    )
    @PostMapping("/portfolio")
    public ApiResponse<PortfolioResponse> updatePortfolio(@RequestBody List<PortfolioResponse.PortfolioItem> items) {
        return ApiResponse.ok(portfolioService.updatePortfolio(items));
    }

    @Operation(summary = "Add stock to portfolio", description = "Adds a stock to the watchlist or portfolio by code and optional name.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stock added successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request body",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Validation error", value = VALIDATION_ERROR)
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to add stock",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @PostMapping("/add_stock")
    public ApiResponse<Void> addStock(@RequestBody AddStockRequest request) {
        portfolioService.addStock(request);
        return ApiResponse.ok(null);
    }

    @Operation(summary = "Remove stock from portfolio", description = "Removes a stock from the watchlist or portfolio by code.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Stock removed successfully"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Invalid request body",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Validation error", value = VALIDATION_ERROR)
            )
        ),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "Failed to remove stock",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ApiResponse.class),
                examples = @ExampleObject(name = "Internal error", value = INTERNAL_ERROR)
            )
        )
    })
    @PostMapping("/remove_stock")
    public ApiResponse<Void> removeStock(@RequestBody RemoveStockRequest request) {
        portfolioService.removeStock(request);
        return ApiResponse.ok(null);
    }
}
