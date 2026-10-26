package com.quantpilot.factor;

import com.quantpilot.domain.Bar;
import com.quantpilot.domain.BarSeries;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 第一版因子集：6 个，全部只用价格和当日快照指标。
 *
 * 刻意不做复杂因子。因子越多越容易过拟合，
 * 而且面试时每个因子都要能讲清经济逻辑——讲不清的不加。
 */
public final class Factors {

    private Factors() {
    }

    /** 20 日动量：短期趋势 */
    public static Factor momentum20() {
        return Factor.of("MOM20", true, (s, d) -> returnOver(s, 20));
    }

    /** 60 日动量：中期趋势 */
    public static Factor momentum60() {
        return Factor.of("MOM60", true, (s, d) -> returnOver(s, 60));
    }

    /** 20 日波动率：低波动溢价，所以越低越好 */
    public static Factor volatility20() {
        return Factor.of("VOL20", false, (s, d) -> {
            List<Double> rets = dailyReturns(s, 20);
            if (rets.size() < 10) {
                return Double.NaN;
            }
            double mean = rets.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double var = rets.stream().mapToDouble(r -> (r - mean) * (r - mean)).sum() / (rets.size() - 1);
            return Math.sqrt(var);
        });
    }

    /** 20 日平均换手率：高换手偏投机，越低越好 */
    public static Factor turnover20() {
        return Factor.of("TURN20", false, (s, d) -> {
            int n = Math.min(20, s.size());
            double sum = 0;
            int cnt = 0;
            for (int i = s.size() - n; i < s.size(); i++) {
                sum += s.get(i).turnoverRate();
                cnt++;
            }
            return cnt == 0 ? Double.NaN : sum / cnt;
        });
    }

    /** 盈利收益率 EP = 1/PE：低估值 */
    public static Factor ep() {
        return Factor.of("EP", true, (s, d) -> {
            Bar last = s.last();
            if (last == null || last.pe() == null || last.pe() <= 0) {
                return Double.NaN;
            }
            return 1.0 / last.pe();
        });
    }

    /** 账面市值比 BP = 1/PB：低估值 */
    public static Factor bp() {
        return Factor.of("BP", true, (s, d) -> {
            Bar last = s.last();
            if (last == null || last.pb() == null || last.pb() <= 0) {
                return Double.NaN;
            }
            return 1.0 / last.pb();
        });
    }

    public static List<Factor> defaultSet() {
        return List.of(momentum20(), momentum60(), volatility20(), turnover20(), ep(), bp());
    }

    // ---------- 工具 ----------

    private static double returnOver(BarSeries s, int n) {
        if (s.size() < n + 1) {
            return Double.NaN;
        }
        double now = s.nthFromEnd(1).close();
        double then = s.nthFromEnd(n + 1).close();
        return then <= 0 ? Double.NaN : now / then - 1.0;
    }

    private static List<Double> dailyReturns(BarSeries s, int n) {
        int start = Math.max(1, s.size() - n);
        List<Double> rets = new ArrayList<>();
        for (int i = start; i < s.size(); i++) {
            double prev = s.get(i - 1).close();
            double cur = s.get(i).close();
            if (prev > 0) {
                rets.add(cur / prev - 1.0);
            }
        }
        return rets;
    }
}
