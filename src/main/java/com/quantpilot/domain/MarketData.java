package com.quantpilot.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全市场数据快照。
 *
 * 只有两个入口：按代码取序列、取交易日历。
 * 上层（因子、回测）不允许直接碰原始列表，避免绕过 asOf 截断。
 */
public final class MarketData {

    private final Map<String, StockInfo> stocks;
    private final Map<String, BarSeries> series;
    private final java.util.NavigableSet<LocalDate> calendar;

    public MarketData(Map<String, StockInfo> stocks, Map<String, BarSeries> series) {
        this.stocks = new LinkedHashMap<>(stocks);
        this.series = new LinkedHashMap<>(series);
        java.util.TreeSet<LocalDate> cal = new java.util.TreeSet<>();
        for (BarSeries s : series.values()) {
            for (Bar b : s.all()) {
                cal.add(b.date());
            }
        }
        this.calendar = java.util.Collections.unmodifiableNavigableSet(cal);
    }

    public StockInfo stock(String code) {
        return stocks.get(code);
    }

    public BarSeries series(String code) {
        return series.get(code);
    }

    public Collection<String> codes() {
        return series.keySet();
    }

    public Collection<StockInfo> stocks() {
        return stocks.values();
    }

    public java.util.NavigableSet<LocalDate> calendar() {
        return calendar;
    }

    public LocalDate firstDate() {
        return calendar.isEmpty() ? null : calendar.first();
    }

    public LocalDate lastDate() {
        return calendar.isEmpty() ? null : calendar.last();
    }

    /** 某个日期的前一个交易日（用于「T 日调仓、用 T-1 数据」） */
    public LocalDate previousTradingDay(LocalDate date) {
        LocalDate prev = calendar.lower(date);
        return prev;
    }
}
