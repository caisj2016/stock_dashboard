package com.caisj.stockdashboard.backend.service;

import com.caisj.stockdashboard.backend.domain.model.MacdSeries;
import com.caisj.stockdashboard.backend.domain.model.MacdSnapshot;
import java.util.List;

/**
 * 提供技术指标计算能力。
 * 负责生成 RSI、SMA 和 MACD 等指标数据，供图表与选股逻辑使用。
 */
public interface IndicatorService {
    /**
     * 计算给定收盘价序列的 RSI 值。
     */
    Double calculateRsi(List<Double> closes, int period);

    /**
     * 计算给定序列的简单移动平均值。
     */
    Double simpleMovingAverage(List<Double> values, int period);

    /**
     * 计算给定序列的简单移动平均值序列。
     */
    List<Double> simpleMovingAverageSeries(List<Double> values, int period);

    /**
     * 计算 MACD 快照，包括当前值、信号线和直方图。
     */
    MacdSnapshot calculateMacd(List<Double> closes);

    /**
     * 计算 MACD 完整时间序列数据，供图表展示使用。
     */
    MacdSeries calculateMacdFullSeries(List<Double> closes);
}
