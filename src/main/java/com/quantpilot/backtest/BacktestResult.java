package com.quantpilot.backtest;

import java.time.LocalDate;
import java.util.List;

/**
 * 回测结果。
 *
 * nav 是从 1.0 开始的净值序列，metrics 在构造时一次算好。
 */
public record BacktestResult(
        List<NavPoint> nav,
        List<Trade> trades,
        List<Rebalance> rebalances,
        Metrics metrics
) {
    public record NavPoint(LocalDate date, double nav, double equity) {
    }

    public record Rebalance(LocalDate date, List<String> picked, int traded) {
    }
}
