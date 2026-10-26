package com.quantpilot.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NavigableSet;

/**
 * 一只股票的时间序列。
 *
 * 设计要点：**所有读取都必须经过 {@link #asOf(LocalDate)}**。
 * 这样「前视偏差」不是靠自觉避免，而是结构上就取不到未来数据。
 */
public final class BarSeries {

    private final String code;
    private final List<Bar> bars; // 按日期升序

    public BarSeries(String code, List<Bar> bars) {
        this.code = code;
        List<Bar> copy = new ArrayList<>(bars);
        copy.sort((a, b) -> a.date().compareTo(b.date()));
        this.bars = Collections.unmodifiableList(copy);
    }

    public String code() {
        return code;
    }

    public List<Bar> all() {
        return bars;
    }

    /** 截断到 asOf（含当天）——这是防前视的唯一入口 */
    public BarSeries asOf(LocalDate asOf) {
        List<Bar> out = new ArrayList<>();
        for (Bar b : bars) {
            if (b.date().isAfter(asOf)) {
                break;
            }
            out.add(b);
        }
        return new BarSeries(code, out);
    }

    public int size() {
        return bars.size();
    }

    public Bar last() {
        return bars.isEmpty() ? null : bars.get(bars.size() - 1);
    }

    public Bar get(int index) {
        return bars.get(index);
    }

    /** 取倒数第 n 根（n=1 表示最后一根） */
    public Bar nthFromEnd(int n) {
        int idx = bars.size() - n;
        return idx < 0 ? null : bars.get(idx);
    }

    public LocalDate firstDate() {
        return bars.isEmpty() ? null : bars.get(0).date();
    }

    public LocalDate lastDate() {
        return bars.isEmpty() ? null : bars.get(bars.size() - 1).date();
    }

    /** 在某个交易日是否有可交易的 bar（停牌时为 false） */
    public boolean hasBarOn(LocalDate date) {
        Bar last = last();
        return last != null && last.date().equals(date);
    }

    public Bar barOn(LocalDate date) {
        Bar last = last();
        return last != null && last.date().equals(date) ? last : null;
    }

    public static NavigableSet<LocalDate> calendar(List<BarSeries> seriesList) {
        java.util.TreeSet<LocalDate> set = new java.util.TreeSet<>();
        for (BarSeries s : seriesList) {
            for (Bar b : s.all()) {
                set.add(b.date());
            }
        }
        return Collections.unmodifiableNavigableSet(set);
    }
}
