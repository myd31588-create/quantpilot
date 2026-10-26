package com.quantpilot.backtest;

import java.util.List;

/**
 * 回测指标。
 *
 * 几个定义上的取舍：
 *  - 年化收益用 252 个交易日折算，不是自然日
 *  - 夏普的无风险利率取 0（A 股研究里常见简化，写清楚比假装精确好）
 *  - 最大回撤是"从历史最高点跌下来"的最大幅度，用来衡量最坏持有体验
 */
public record Metrics(
        double totalReturn,
        double annualizedReturn,
        double annualizedVolatility,
        double sharpe,
        double maxDrawdown,
        double winRate,
        int tradingDays
) {

    public static Metrics of(List<BacktestResult.NavPoint> nav) {
        if (nav == null || nav.size() < 2) {
            return new Metrics(0, 0, 0, 0, 0, 0, 0);
        }
        double first = nav.get(0).nav();
        double last = nav.get(nav.size() - 1).nav();
        double total = last / first - 1.0;

        int n = nav.size() - 1;
        double annualized = Math.pow(1 + total, 252.0 / n) - 1;

        double[] rets = new double[n];
        int wins = 0;
        for (int i = 1; i < nav.size(); i++) {
            double r = nav.get(i).nav() / nav.get(i - 1).nav() - 1.0;
            rets[i - 1] = r;
            if (r > 0) {
                wins++;
            }
        }
        double mean = 0;
        for (double r : rets) {
            mean += r;
        }
        mean /= n;
        double var = 0;
        for (double r : rets) {
            var += (r - mean) * (r - mean);
        }
        var /= Math.max(1, n - 1);
        double dailyStd = Math.sqrt(var);
        double annualVol = dailyStd * Math.sqrt(252);
        double sharpe = annualVol < 1e-12 ? 0 : annualized / annualVol;

        double peak = nav.get(0).nav();
        double maxDd = 0;
        for (BacktestResult.NavPoint p : nav) {
            peak = Math.max(peak, p.nav());
            maxDd = Math.max(maxDd, (peak - p.nav()) / peak);
        }

        return new Metrics(total, annualized, annualVol, sharpe, maxDd, (double) wins / n, n);
    }
}
