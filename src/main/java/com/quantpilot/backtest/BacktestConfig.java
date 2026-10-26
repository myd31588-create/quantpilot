package com.quantpilot.backtest;

import java.time.LocalDate;

/**
 * 回测参数。
 *
 * 默认值都按 A 股实际规则给：佣金万 2.5（最低 5 元）、印花税千 1（卖出）、
 * 滑点单边 0.1%、涨跌停 ±10%。
 *
 * signalLag 是"信号滞后天数"，默认 0（即用 T-1 数据在 T 日开盘成交）。
 * 把它调大，就是"晚几天才动手"——这是审计里检测前视偏差的核心手段。
 */
public record BacktestConfig(
        LocalDate start,
        LocalDate end,
        int topN,
        int rebalanceMonths,
        double commissionRate,
        double minCommission,
        double stampTaxRate,
        double slippage,
        double limitPct,
        double initialCash,
        int signalLag
) {
    public static BacktestConfig defaults(LocalDate start, LocalDate end) {
        return new BacktestConfig(start, end, 10, 1, 0.00025, 5.0, 0.001, 0.001, 0.10, 1_000_000, 0);
    }

    public BacktestConfig withSignalLag(int lag) {
        return new BacktestConfig(start, end, topN, rebalanceMonths, commissionRate, minCommission,
                stampTaxRate, slippage, limitPct, initialCash, lag);
    }

    public BacktestConfig withTopN(int n) {
        return new BacktestConfig(start, end, n, rebalanceMonths, commissionRate, minCommission,
                stampTaxRate, slippage, limitPct, initialCash, signalLag);
    }

    public BacktestConfig withCostMultiplier(double k) {
        return new BacktestConfig(start, end, topN, rebalanceMonths,
                commissionRate * k, minCommission * k, stampTaxRate * k, slippage * k,
                limitPct, initialCash, signalLag);
    }
}
