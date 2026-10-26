package com.quantpilot.data;

import com.quantpilot.domain.Bar;
import com.quantpilot.domain.BarSeries;
import com.quantpilot.domain.MarketData;
import com.quantpilot.domain.StockInfo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 合成行情生成器。
 *
 * 为什么需要它：
 *  1. 开源仓库不能带真实行情（版权），但 demo 必须能跑
 *  2. 面试现场没有网络也能演示
 *  3. 测试需要可复现的数据（固定 seed）
 *
 * 生成的是"有结构的随机游走"：不同股票有不同的漂移和波动，
 * 这样因子才会有区分度，回测结果才会有意义（虽然不代表真实收益）。
 */
public final class SyntheticMarketDataProvider implements MarketDataProvider {

    private final int stockCount;
    private final LocalDate start;
    private final LocalDate end;
    private final long seed;

    public SyntheticMarketDataProvider(int stockCount, LocalDate start, LocalDate end, long seed) {
        this.stockCount = stockCount;
        this.start = start;
        this.end = end;
        this.seed = seed;
    }

    public static SyntheticMarketDataProvider defaults() {
        return new SyntheticMarketDataProvider(60, LocalDate.of(2022, 1, 3), LocalDate.of(2024, 12, 31), 20260101L);
    }

    @Override
    public String name() {
        return "synthetic";
    }

    @Override
    public MarketData load() {
        List<LocalDate> days = tradingDays();
        Map<String, StockInfo> stocks = new LinkedHashMap<>();
        Map<String, BarSeries> series = new LinkedHashMap<>();

        Random rnd = new Random(seed);
        for (int i = 0; i < stockCount; i++) {
            String code = String.format("%06d", 600000 + i);
            boolean st = i % 17 == 0;
            // 前 3 只晚上市，用来验证「上市满 N 天」的过滤
            LocalDate listed = i < 3 ? start.plusDays(200) : start.minusYears(3);
            StockInfo info = new StockInfo(code, "SYN" + i, "行业" + (i % 6), st, listed, null);
            stocks.put(code, info);

            double drift = (rnd.nextDouble() - 0.45) * 0.0012;   // 有正有负
            double vol = 0.012 + rnd.nextDouble() * 0.02;
            double price = 8 + rnd.nextDouble() * 40;
            double eps = 0.2 + rnd.nextDouble() * 1.5;

            List<Bar> bars = new ArrayList<>();
            for (LocalDate d : days) {
                if (d.isBefore(listed)) {
                    continue;
                }
                // 1% 概率停牌：当天没有 bar
                if (rnd.nextDouble() < 0.01) {
                    continue;
                }
                double ret = drift + rnd.nextGaussian() * vol;
                ret = Math.max(-0.095, Math.min(0.095, ret));   // 不越过涨跌停
                double prev = price;
                price = Math.max(1.0, price * (1 + ret));

                double open = prev * (1 + (rnd.nextDouble() - 0.5) * 0.01);
                double high = Math.max(open, price) * (1 + rnd.nextDouble() * 0.01);
                double low = Math.min(open, price) * (1 - rnd.nextDouble() * 0.01);
                long volume = (long) (2_000_000 + rnd.nextDouble() * 20_000_000);
                double amount = volume * price / 10_000.0;   // 单位：万元
                double turnover = 0.5 + rnd.nextDouble() * 5.0;
                Double pe = price / eps;
                Double pb = 1 + rnd.nextDouble() * 6;

                bars.add(new Bar(d, round2(open), round2(high), round2(low), round2(price),
                        volume, round2(amount), round2(turnover), round2(pe), round2(pb)));
            }
            series.put(code, new BarSeries(code, bars));
        }
        return new MarketData(stocks, series);
    }

    private List<LocalDate> tradingDays() {
        List<LocalDate> days = new ArrayList<>();
        LocalDate d = start;
        while (!d.isAfter(end)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(d);
            }
            d = d.plusDays(1);
        }
        return days;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
