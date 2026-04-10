package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "ScreenerResponse", description = "Screened stock list with summary metadata and chart snippets.")
public record ScreenerResponse(
    @Schema(description = "Screener mode applied to the universe.", example = "combo")
    String mode,
    @Schema(description = "Universe key used internally.", example = "core45")
    String universeKey,
    @Schema(description = "Display name of the selected universe.", example = "Core 45")
    String universe,
    @Schema(description = "Number of symbols available in the selected universe.", example = "45")
    Integer universeSize,
    @Schema(description = "Short description of the selected universe.", example = "Core Japanese large-cap watchlist")
    String universeDescription,
    @Schema(description = "Timestamp string for the generated screener snapshot.", example = "2026-04-10 12:30 JST")
    String updated,
    @Schema(description = "Requested maximum number of results.", example = "18")
    int limit,
    @Schema(description = "Actual number of returned result cards.", example = "12")
    int count,
    @Schema(description = "Optional warning shown when the screener had partial upstream failures.", nullable = true)
    String warning,
    @Schema(description = "Screened result cards.")
    List<ScreenerItem> items
) {

    @Schema(name = "ScreenerItem", description = "Single screener card returned to the frontend.")
    public record ScreenerItem(
        @Schema(description = "Ticker symbol.", example = "7203.T")
        String symbol,
        @Schema(description = "Display name of the company.", example = "Toyota Motor")
        String name,
        @Schema(description = "Latest price.", example = "2840.5")
        Double price,
        @Schema(description = "Latest percentage change versus previous close.", example = "1.24")
        Double changePct,
        @Schema(description = "Latest 14-period RSI.", example = "46.8")
        Double rsi14,
        @Schema(description = "Latest MACD line value.", example = "12.3")
        Double macd,
        @Schema(description = "Latest MACD signal value.", example = "10.4")
        Double macdSignal,
        @Schema(description = "Latest MACD histogram value.", example = "1.9")
        Double macdHist,
        @Schema(description = "Recent volume ratio.", example = "1.35")
        Double volumeRatio,
        @Schema(description = "Latest 5-period moving average.", example = "2810.2")
        Double ma5,
        @Schema(description = "Latest 20-period moving average.", example = "2765.8")
        Double ma20,
        @Schema(description = "Latest 60-period moving average.", example = "2698.6")
        Double ma60,
        @Schema(description = "Whether MACD has a bullish crossover condition.")
        boolean macdBullCross,
        @Schema(description = "Whether the short moving average crossed above a longer moving average.")
        boolean maCross,
        @Schema(description = "Whether price is currently above the 20-period moving average.")
        boolean aboveMa20,
        @Schema(description = "Whether price is breaking out above the 20-period range.")
        boolean breakout20,
        @Schema(description = "Combined screener score used for ranking.", example = "78")
        Integer score,
        @Schema(description = "Whether the symbol is already in the portfolio or watchlist.")
        boolean inWatchlist,
        @Schema(description = "Recent open prices used by the mini chart.")
        List<Double> opens20,
        @Schema(description = "Recent high prices used by the mini chart.")
        List<Double> highs20,
        @Schema(description = "Recent low prices used by the mini chart.")
        List<Double> lows20,
        @Schema(description = "Recent close prices used by the mini chart.")
        List<Double> closes20,
        @Schema(description = "Recent volumes used by the mini chart.")
        List<Long> volumes20,
        @Schema(description = "Recent MACD series values used by the mini chart.")
        List<Double> macdSeries,
        @Schema(description = "Recent MACD signal series values used by the mini chart.")
        List<Double> signalSeries,
        @Schema(description = "Recent MACD histogram values used by the mini chart.")
        List<Double> histSeries,
        @Schema(description = "Human-readable screener signals displayed on the card.")
        List<String> signals
    ) {
    }
}
