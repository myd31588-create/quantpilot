package com.quantpilot.factor;

import com.quantpilot.domain.BarSeries;

import java.time.LocalDate;

/**
 * 一个因子 = 名字 + 方向 + 计算函数。
 *
 * 关键约束：计算函数拿到的必须是已经 asOf 截断过的序列，
 * 所以它**物理上取不到未来数据**。这是防前视偏差的第一道闸。
 */
public record Factor(String name, boolean higherIsBetter, Fn fn) {

    @FunctionalInterface
    public interface Fn {
        double compute(BarSeries series, LocalDate asOf);
    }

    public static Factor of(String name, boolean higherIsBetter, Fn fn) {
        return new Factor(name, higherIsBetter, fn);
    }

    public double value(BarSeries raw, LocalDate asOf) {
        if (raw == null) {
            return Double.NaN;
        }
        BarSeries s = raw.asOf(asOf);
        if (s.size() < 2) {
            return Double.NaN;
        }
        return fn.compute(s, asOf);
    }
}
