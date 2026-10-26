package com.quantpilot.backtest;

import com.quantpilot.domain.Bar;
import com.quantpilot.domain.BarSeries;
import com.quantpilot.domain.MarketData;
import com.quantpilot.domain.StockInfo;
import com.quantpilot.factor.Factors;
import com.quantpilot.strategy.Selector;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BacktestEngineTest {

    private static final LocalDate PRE = LocalDate.of(2023, 12, 29);
    private static final LocalDate START = LocalDate.of(2024, 1, 2);
    private static final LocalDate END = LocalDate.of(2024, 3, 29);

    @Test
    void 一字涨停时买不进() {
        MarketData data = market(Map.of("600001", List.of(
                bar(PRE, 10.0, 10.0),
                bar(LocalDate.of(2024, 1, 2), 11.0, 11.0),   // 开盘即 +10%
                bar(LocalDate.of(2024, 1, 3), 12.1, 12.1)
        )));

        BacktestResult r = run(data, List.of("600001"), costs());

        boolean bought = r.trades().stream().anyMatch(t ->
                t.code().equals("600001") && t.side() == Trade.Side.BUY
                        && t.date().equals(LocalDate.of(2024, 1, 2)));
        assertFalse(bought, "一字涨停买不进");
    }

    @Test
    void 停牌当天卖不出() {
        LocalDate suspended = LocalDate.of(2024, 2, 1);
        MarketData data = market(Map.of("600001", seriesSkipping(10.0, 0.001, suspended)));

        BacktestResult r = run(data, List.of("600001"), costs());

        boolean soldOnSuspension = r.trades().stream().anyMatch(t ->
                t.side() == Trade.Side.SELL && t.date().equals(suspended));
        assertFalse(soldOnSuspension, "停牌当天不应有卖出成交");
    }

    @Test
    void 计入交易成本后收益更低() {
        MarketData data = market(Map.of(
                "600001", seriesSkipping(10.0, 0.002, null),
                "600002", seriesSkipping(20.0, -0.001, null)
        ));

        BacktestResult withCost = run(data, List.of("600001"), costs());
        BacktestResult noCost = run(data, List.of("600001"), new BacktestConfig(
                START, END, 5, 1, 0, 0, 0, 0, 0.10, 1_000_000, 0));

        assertTrue(noCost.metrics().totalReturn() >= withCost.metrics().totalReturn(),
                "零成本回测收益不应低于含成本回测");
        assertTrue(withCost.trades().stream().anyMatch(t -> t.fee() > 0), "应当产生手续费");
    }

    @Test
    void 净值从1开始且按月调仓() {
        MarketData data = market(Map.of("600001", seriesSkipping(10.0, 0.001, null)));
        BacktestResult r = run(data, List.of("600001"), costs());

        assertFalse(r.nav().isEmpty());
        // 首日建仓要扣手续费和滑点，所以净值会略低于 1——不能断言恰好等于 1
        assertEquals(1.0, r.nav().get(0).nav(), 0.01, "首日净值应接近 1（仅差手续费与滑点）");
        assertTrue(r.rebalances().size() >= 3, "1–3 月应至少调仓 3 次");
        assertTrue(r.metrics().maxDrawdown() >= 0);
        assertTrue(r.metrics().tradingDays() > 50);
    }

    @Test
    void 首次调仓应真实建仓_不能因为手续费差一点就整仓跳过() {
        MarketData data = market(Map.of("600001", seriesSkipping(10.0, 0.001, null)));
        BacktestResult r = run(data, List.of("600001"), costs());

        assertFalse(r.trades().isEmpty(), "应当有成交");
        double bought = r.trades().stream()
                .filter(t -> t.side() == Trade.Side.BUY)
                .mapToDouble(Trade::amount)
                .sum();
        assertTrue(bought > 900_000, "首笔建仓应基本打满 100 万本金，实际 " + bought);
    }

    // ---------- 工具 ----------

    private static BacktestConfig costs() {
        return BacktestConfig.defaults(START, END);
    }

    private BacktestResult run(MarketData data, List<String> picks, BacktestConfig cfg) {
        Selector fixed = new Selector() {
            @Override
            public List<String> select(MarketData d, LocalDate asOf,
                                       List<WeightedFactor> weights, int topN) {
                return picks;
            }
        };
        List<Selector.WeightedFactor> w = List.of(new Selector.WeightedFactor(Factors.momentum20(), 1.0));
        return new BacktestEngine().run(data, fixed, w, cfg);
    }

    private static MarketData market(Map<String, List<Bar>> barsByCode) {
        Map<String, StockInfo> stocks = new LinkedHashMap<>();
        Map<String, BarSeries> series = new LinkedHashMap<>();
        for (Map.Entry<String, List<Bar>> e : barsByCode.entrySet()) {
            stocks.put(e.getKey(), new StockInfo(e.getKey(), e.getKey(), "行业", false,
                    LocalDate.of(2020, 1, 1), null));
            series.put(e.getKey(), new BarSeries(e.getKey(), e.getValue()));
        }
        return new MarketData(stocks, series);
    }

    private static Bar bar(LocalDate d, double open, double close) {
        return new Bar(d, open, Math.max(open, close) * 1.01, Math.min(open, close) * 0.99,
                close, 1_000_000, 5000, 1.0, 20.0, 2.0);
    }

    /** 生成从 PRE 到 END 的连续日线；skip 日期那天不生成（模拟停牌） */
    private static List<Bar> seriesSkipping(double startPrice, double dailyRet, LocalDate skip) {
        List<Bar> bars = new ArrayList<>();
        LocalDate d = PRE;
        double p = startPrice;
        while (!d.isAfter(END)) {
            if (d.getDayOfWeek() != DayOfWeek.SATURDAY && d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                p = p * (1 + dailyRet);
                if (!d.equals(skip)) {
                    bars.add(bar(d, p, p));
                }
            }
            d = d.plusDays(1);
        }
        return bars;
    }
}
