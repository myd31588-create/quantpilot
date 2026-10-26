package com.quantpilot.audit;

import com.quantpilot.backtest.BacktestConfig;
import com.quantpilot.backtest.BacktestEngine;
import com.quantpilot.domain.MarketData;
import com.quantpilot.domain.StockInfo;
import com.quantpilot.strategy.Selector;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 回测审计：不评估"赚多少"，只评估"这个结果可不可信"。
 *
 * 五项检查，每一项对应一种典型的自欺欺人：
 *  1. 信号滞后   —— 有没有偷看未来
 *  2. 随机对照   —— 收益是不是运气
 *  3. 参数敏感   —— 是不是拟合出来的
 *  4. 成本敏感   —— 是不是靠零成本假设撑起来的
 *  5. 幸存者偏差 —— 数据里有没有死掉的股票
 */
public class StrategyAuditor {

    private final MarketData data;
    private final Selector selector;
    private final List<Selector.WeightedFactor> weights;
    private final BacktestConfig base;
    private final int permutationRuns;

    public StrategyAuditor(MarketData data, Selector selector,
                           List<Selector.WeightedFactor> weights,
                           BacktestConfig base, int permutationRuns) {
        this.data = data;
        this.selector = selector;
        this.weights = weights;
        this.base = base;
        this.permutationRuns = permutationRuns;
    }

    public AuditReport audit() {
        List<AuditReport.Check> checks = new ArrayList<>();

        double r0 = runReturn(selector, base);

        // 1. 信号滞后：把信号整体延后，收益应"平滑"变化
        //    注意：只看"是否下滑"是不够的——如果延后收益反而暴涨，
        //    同样说明结果不稳定。所以看的是最大偏差，不分方向。
        double r1 = runReturn(selector, base.withSignalLag(1));
        double r2 = runReturn(selector, base.withSignalLag(2));
        double maxDeviation = Math.max(Math.abs(r0 - r1), Math.abs(r0 - r2));
        AuditReport.Status shiftStatus;
        String shiftAdvice;
        if (maxDeviation > 0.15) {
            shiftStatus = AuditReport.Status.FAIL;
            shiftAdvice = "收益随信号时点剧烈波动（不论变好变坏），说明结果不稳定，"
                    + "大概率是噪声而非真实 alpha；若滞后后反而更好，更要检查因子是否用到了未来数据";
        } else if (maxDeviation > 0.05) {
            shiftStatus = AuditReport.Status.WARN;
            shiftAdvice = "对执行时点较敏感，确认因子是否只用到 T-1 及之前的数据";
        } else {
            shiftStatus = AuditReport.Status.PASS;
            shiftAdvice = "收益对信号时点不敏感，符合无前视、且信号较稳的特征";
        }
        checks.add(new AuditReport.Check("信号滞后检验", shiftStatus,
                String.format("滞后 0/1/2 天收益：%.2f%% / %.2f%% / %.2f%%，最大偏差 %.1f 个百分点",
                        r0 * 100, r1 * 100, r2 * 100, maxDeviation * 100), shiftAdvice));

        // 2. 随机对照：同样规则下随机选股
        double[] randoms = IntStream.range(0, permutationRuns)
                .parallel()
                .mapToDouble(i -> runReturn(new RandomSelector(i * 7919L), base))
                .toArray();
        double pValue = 0;
        for (double r : randoms) {
            if (r >= r0) {
                pValue++;
            }
        }
        pValue /= randoms.length;
        double randomMedian = median(randoms);

        AuditReport.Status randStatus;
        String randAdvice;
        if (pValue < 0.05) {
            randStatus = AuditReport.Status.PASS;
            randAdvice = "收益显著高于随机选股，不太可能只是运气";
        } else if (pValue < 0.20) {
            randStatus = AuditReport.Status.WARN;
            randAdvice = "仅略微跑赢随机选股，统计上不显著，样本可能需要更长";
        } else {
            randStatus = AuditReport.Status.FAIL;
            randAdvice = "跑不赢随机选股，当前因子组合没有体现出选股能力";
        }
        checks.add(new AuditReport.Check("随机化对照", randStatus,
                String.format("%d 次随机选股中位数 %.2f%%，策略 %.2f%%，p = %.2f",
                        permutationRuns, randomMedian * 100, r0 * 100, pValue), randAdvice));

        // 3. 参数敏感性：Top N 扫描
        int[] topNs = {5, 10, 15, 20, 30};
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        StringBuilder sb = new StringBuilder("Top N 扫描：");
        for (int n : topNs) {
            double r = runReturn(selector, base.withTopN(n));
            min = Math.min(min, r);
            max = Math.max(max, r);
            sb.append(String.format("%d→%.1f%%  ", n, r * 100));
        }
        double spread = max - min;
        AuditReport.Status paramStatus = spread > 0.25 ? AuditReport.Status.WARN : AuditReport.Status.PASS;
        checks.add(new AuditReport.Check("参数敏感性", paramStatus, sb.toString().trim(),
                spread > 0.25
                        ? "不同 Top N 下收益差异过大，说明结果对参数很敏感，容易过拟合"
                        : "不同参数下收益相对稳定，不像精心拟合出来的"));

        // 4. 成本敏感度
        double r3x = runReturn(selector, base.withCostMultiplier(3));
        double costDrop = r0 - r3x;
        AuditReport.Status costStatus = costDrop > 0.10 ? AuditReport.Status.WARN : AuditReport.Status.PASS;
        checks.add(new AuditReport.Check("成本敏感度", costStatus,
                String.format("成本 ×3 后收益由 %.2f%% 变为 %.2f%%", r0 * 100, r3x * 100),
                costDrop > 0.10
                        ? "高换手策略对成本极敏感，实盘很难维持回测收益"
                        : "对成本不敏感，换手与费率假设较稳健"));

        // 5. 幸存者偏差：数据里有没有退市股票
        long delisted = data.stocks().stream().filter(s -> s.delistedDate() != null).count();
        AuditReport.Status survStatus = delisted == 0 ? AuditReport.Status.WARN : AuditReport.Status.PASS;
        checks.add(new AuditReport.Check("幸存者偏差", survStatus,
                String.format("股票池 %d 只，其中退市 %d 只", data.stocks().size(), delisted),
                delisted == 0
                        ? "数据集中没有退市股票，回测收益可能被高估，建议使用含退市样本的行情"
                        : "包含退市股票，样本覆盖相对完整"));

        int score = 100;
        for (AuditReport.Check c : checks) {
            if (c.status() == AuditReport.Status.FAIL) {
                score -= 25;
            } else if (c.status() == AuditReport.Status.WARN) {
                score -= 10;
            }
        }
        score = Math.max(0, Math.min(100, score));

        return new AuditReport(score, checks, r0, pValue, permutationRuns);
    }

    private double runReturn(Selector s, BacktestConfig cfg) {
        return new BacktestEngine().run(data, s, weights, cfg).metrics().totalReturn();
    }

    private static double median(double[] values) {
        double[] copy = values.clone();
        java.util.Arrays.sort(copy);
        int n = copy.length;
        return n % 2 == 1 ? copy[n / 2] : (copy[n / 2 - 1] + copy[n / 2]) / 2.0;
    }
}
