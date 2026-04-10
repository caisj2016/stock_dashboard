package com.caisj.stockdashboard.backend.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "ChartHistoryResponse", description = "Chart-ready historical price series and derived technical indicators.")
public record ChartHistoryResponse(
    @Schema(description = "Whether the chart payload was built successfully.", example = "true")
    boolean ok,
    @Schema(description = "Ticker symbol requested by the page.", example = "7203.T")
    String symbol,
    @Schema(description = "Display name of the stock or instrument.", example = "Toyota Motor")
    String name,
    @Schema(description = "Interval code used to build the series.", example = "D")
    String interval,
    @Schema(description = "Human-readable label for the selected interval.", example = "Daily")
    String label,
    @Schema(description = "Error message when chart loading fails.", nullable = true)
    String error,
    @Schema(description = "Date labels aligned with each candle.", example = "[\"2026-04-01\",\"2026-04-02\"]")
    List<String> dates,
    @Schema(description = "Unix timestamps aligned with each candle, in milliseconds.")
    List<Long> timestamps,
    @Schema(description = "Open prices for each candle.")
    List<Double> opens,
    @Schema(description = "High prices for each candle.")
    List<Double> highs,
    @Schema(description = "Low prices for each candle.")
    List<Double> lows,
    @Schema(description = "Close prices for each candle.")
    List<Double> closes,
    @Schema(description = "Trading volume for each candle.")
    List<Long> volumes,
    @Schema(description = "5-period simple moving average series.")
    List<Double> ma5,
    @Schema(description = "20-period simple moving average series.")
    List<Double> ma20,
    @Schema(description = "MACD line series.")
    List<Double> macd,
    @Schema(description = "MACD signal line series.")
    List<Double> signal,
    @Schema(description = "MACD histogram series.")
    List<Double> hist,
    @Schema(description = "Latest price used by the chart header.", example = "2840.5")
    Double price,
    @Schema(description = "Latest percentage change versus previous close.", example = "1.24")
    Double changePct,
    @Schema(description = "Latest 14-period RSI value.", example = "46.8")
    Double rsi14,
    @Schema(description = "Latest volume ratio derived from recent candles.", example = "1.35")
    Double volumeRatio,
    @Schema(description = "Timestamp string used for display in the page header.", example = "2026-04-10 12:30 JST")
    String updated
) {
}
