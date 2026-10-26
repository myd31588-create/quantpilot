package com.quantpilot.strategy;

import com.quantpilot.domain.Bar;
import com.quantpilot.domain.MarketData;
import com.quantpilot.domain.StockInfo;
import com.quantpilot.factor.Factor;
import com.quantpilot.factor.FactorEngine;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多因子选股。
 *
 * 流程：过滤 → 标准化 → 加权打分 → 排序取 Top N
 *
 * 过滤必须在打分之前，否则被过滤的股票会污染截面分布
 * （比如一只停牌股的极端因子值会把均值拉偏）。
 */
public class Selector {

    /** 过滤器参数 */
    public record FilterConfig(
            int minListedDays,
            double minAmount,        // 最小成交额（万元），太低流动性不够
            boolean excludeSt
    ) {
        public static FilterConfig defaults() {
            return new FilterConfig(60, 500, true);
        }
    }

    private final FactorEngine factorEngine = new FactorEngine();
    private final FilterConfig filter;

    public Selector(FilterConfig filter) {
        this.filter = filter;
    }

    public Selector() {
        this(FilterConfig.defaults());
    }

    /**
     * @param weights 因子与权重，权重和为 1 即可（不强制归一）
     * @return 按分数倒序的候选股票
     */
    public List<String> select(MarketData data, LocalDate asOf, List<WeightedFactor> weights, int topN) {
        List<String> eligible = filterEligible(data, asOf);
        List<Factor> factors = weights.stream().map(WeightedFactor::factor).toList();

        Map<String, Map<String, Double>> raw = new LinkedHashMap<>();
        for (Factor f : factors) {
            Map<String, Double> v = new LinkedHashMap<>();
            for (String code : eligible) {
                double x = f.value(data.series(code), asOf);
                if (!Double.isNaN(x) && !Double.isInfinite(x)) {
                    v.put(code, x);
                }
            }
            raw.put(f.name(), v);
        }
        Map<String, Map<String, Double>> std = factorEngine.standardize(raw);

        Map<String, Double> score = new LinkedHashMap<>();
        for (String code : eligible) {
            double s = 0;
            boolean missing = false;
            for (WeightedFactor wf : weights) {
                Double v = std.getOrDefault(wf.factor().name(), Map.of()).get(code);
                if (v == null) {
                    missing = true;
                    break;
                }
                double signed = wf.factor().higherIsBetter() ? v : -v;
                s += signed * wf.weight();
            }
            if (!missing) {
                score.put(code, s);
            }
        }

        return score.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .map(Map.Entry::getKey)
                .toList();
    }

    private List<String> filterEligible(MarketData data, LocalDate asOf) {
        List<String> out = new ArrayList<>();
        for (String code : data.codes()) {
            StockInfo info = data.stock(code);
            if (info == null || !info.isListedOn(asOf)) {
                continue;
            }
            if (filter.excludeSt() && info.st()) {
                continue;
            }
            if (!info.listedAtLeast(asOf, filter.minListedDays())) {
                continue;
            }
            Bar bar = data.series(code).asOf(asOf).last();
            // 当天停牌（没有 bar）或流动性不足
            if (bar == null || !bar.date().equals(asOf) || bar.amount() < filter.minAmount()) {
                continue;
            }
            out.add(code);
        }
        return out;
    }

    /** 因子 + 权重 */
    public record WeightedFactor(Factor factor, double weight) {
    }
}
