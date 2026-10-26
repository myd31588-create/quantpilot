package com.quantpilot.factor;

import com.quantpilot.domain.MarketData;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 因子计算 + 截面标准化。
 *
 * 标准化流程（顺序不能反）：
 *   1. 去极值：MAD 法，把离群值截断到 ±3 倍中位数绝对偏差
 *   2. Z-score：减均值除标准差
 *
 * 为什么不先 Z-score 再去极值：极端值会把均值和标准差都带偏，
 * 先标准化等于让离群点污染整个分布。
 */
public final class FactorEngine {

    private static final double MAD_SCALE = 1.4826; // 让 MAD 在正态分布下无偏估计标准差

    /** 原始因子值：factorName -> code -> value */
    public Map<String, Map<String, Double>> raw(MarketData data, LocalDate asOf, List<Factor> factors) {
        Map<String, Map<String, Double>> result = new LinkedHashMap<>();
        for (Factor f : factors) {
            Map<String, Double> values = new LinkedHashMap<>();
            for (String code : data.codes()) {
                double v = f.value(data.series(code), asOf);
                if (!Double.isNaN(v) && !Double.isInfinite(v)) {
                    values.put(code, v);
                }
            }
            result.put(f.name(), values);
        }
        return result;
    }

    /** 标准化后的因子值，范围大致在 -3 ~ 3 */
    public Map<String, Map<String, Double>> standardize(Map<String, Map<String, Double>> raw) {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Double>> e : raw.entrySet()) {
            out.put(e.getKey(), standardizeOne(e.getValue()));
        }
        return out;
    }

    private Map<String, Double> standardizeOne(Map<String, Double> values) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (values.size() < 3) {
            values.forEach((k, v) -> out.put(k, 0.0));
            return out;
        }

        List<Double> sorted = new ArrayList<>(values.values());
        sorted.sort(Comparator.naturalOrder());
        double median = median(sorted);
        List<Double> deviations = new ArrayList<>();
        for (double v : sorted) {
            deviations.add(Math.abs(v - median));
        }
        double mad = median(deviations);
        double lo = Double.NEGATIVE_INFINITY;
        double hi = Double.POSITIVE_INFINITY;
        if (mad > 1e-12) {
            lo = median - 3 * MAD_SCALE * mad;
            hi = median + 3 * MAD_SCALE * mad;
        }

        List<Double> clipped = new ArrayList<>();
        for (double v : values.values()) {
            clipped.add(Math.max(lo, Math.min(hi, v)));
        }
        double mean = clipped.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double var = clipped.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum() / clipped.size();
        double std = Math.sqrt(var);

        for (Map.Entry<String, Double> e : values.entrySet()) {
            double v = Math.max(lo, Math.min(hi, e.getValue()));
            out.put(e.getKey(), std < 1e-12 ? 0.0 : (v - mean) / std);
        }
        return out;
    }

    private static double median(List<Double> sorted) {
        int n = sorted.size();
        if (n == 0) {
            return 0;
        }
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }
}
