package com.quantpilot.backtest;

import com.quantpilot.domain.Bar;
import com.quantpilot.domain.BarSeries;
import com.quantpilot.domain.MarketData;
import com.quantpilot.strategy.Selector;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 事件驱动回测引擎。
 *
 * 核心规则（每一条都对应一个真实的坑）：
 *  1. T 日开盘调仓，只允许用 T-1 收盘后的数据选股 —— 防前视偏差
 *  2. 停牌不能交易；涨停买不进、跌停卖不出 —— 防"假设总能成交"
 *  3. 买入按 100 股一手向下取整 —— A 股最小交易单位
 *  4. 买卖都扣佣金，卖出加印花税，成交价含滑点 —— 不建模成本的回测是自欺欺人
 */
public class BacktestEngine {

    private static final int LOT_SIZE = 100;

    public BacktestResult run(MarketData data,
                              Selector selector,
                              List<Selector.WeightedFactor> weights,
                              BacktestConfig cfg) {

        List<LocalDate> days = data.calendar().stream()
                .filter(d -> !d.isBefore(cfg.start()) && !d.isAfter(cfg.end()))
                .toList();
        if (days.size() < 2) {
            return new BacktestResult(List.of(), List.of(), List.of(),
                    Metrics.of(List.of()));
        }

        Cash wallet = new Cash(cfg.initialCash());
        Map<String, Long> holdings = new LinkedHashMap<>();
        List<BacktestResult.NavPoint> navPoints = new ArrayList<>();
        List<Trade> trades = new ArrayList<>();
        List<BacktestResult.Rebalance> rebalances = new ArrayList<>();

        YearMonth startMonth = YearMonth.from(days.get(0));
        YearMonth lastMonth = null;
        int everyMonths = Math.max(1, cfg.rebalanceMonths());

        for (LocalDate date : days) {
            YearMonth ym = YearMonth.from(date);
            boolean monthChanged = lastMonth == null || !ym.equals(lastMonth);
            boolean shouldRebalance = monthChanged
                    && Math.floorMod((int) startMonth.until(ym, java.time.temporal.ChronoUnit.MONTHS), everyMonths) == 0;
            lastMonth = ym;

            if (shouldRebalance) {
                LocalDate signalDate = date;
                for (int i = 0; i <= cfg.signalLag(); i++) {
                    signalDate = data.previousTradingDay(signalDate);
                    if (signalDate == null) {
                        break;
                    }
                }
                if (signalDate != null) {
                    List<String> picks = selector.select(data, signalDate, weights, cfg.topN());
                    int traded = sellAll(data, date, holdings, wallet, trades, cfg);
                    traded += buyPicks(data, date, picks, holdings, wallet, trades, cfg);
                    rebalances.add(new BacktestResult.Rebalance(date, picks, traded));
                }
            }

            double equity = wallet.value + holdingsValue(data, date, holdings);
            navPoints.add(new BacktestResult.NavPoint(date, equity / cfg.initialCash(), equity));
        }

        return new BacktestResult(navPoints, trades, rebalances, Metrics.of(navPoints));
    }

    // ---------- 交易 ----------

    private int sellAll(MarketData data, LocalDate date, Map<String, Long> holdings,
                        Cash cash, List<Trade> trades, BacktestConfig cfg) {
        int n = 0;
        List<String> codes = new ArrayList<>(holdings.keySet());
        for (String code : codes) {
            long shares = holdings.get(code);
            if (shares <= 0) {
                holdings.remove(code);
                continue;
            }
            BarSeries s = data.series(code).asOf(date);
            Bar bar = s.barOn(date);
            if (bar == null) {
                continue; // 停牌，卖不掉
            }
            if (isLimitDown(s, bar, cfg.limitPct())) {
                continue; // 跌停，卖不出
            }
            double price = bar.open() * (1 - cfg.slippage());
            double amount = price * shares;
            double fee = Math.max(cfg.minCommission(), amount * cfg.commissionRate())
                    + amount * cfg.stampTaxRate();
            cash.value += amount - fee;
            trades.add(new Trade(date, code, Trade.Side.SELL, round2(price), shares, round2(amount), round2(fee)));
            holdings.remove(code);
            n++;
        }
        return n;
    }

    private int buyPicks(MarketData data, LocalDate date, List<String> picks,
                         Map<String, Long> holdings, Cash cash, List<Trade> trades, BacktestConfig cfg) {
        List<String> buyable = new ArrayList<>();
        for (String code : picks) {
            BarSeries s = data.series(code).asOf(date);
            Bar bar = s.barOn(date);
            if (bar == null || isLimitUp(s, bar, cfg.limitPct())) {
                continue;
            }
            buyable.add(code);
        }
        if (buyable.isEmpty() || cash.value <= 0) {
            return 0;
        }

        double perStock = cash.value / buyable.size();
        int n = 0;
        for (String code : buyable) {
            Bar bar = data.series(code).asOf(date).barOn(date);
            double price = bar.open() * (1 + cfg.slippage());

            // 按预算算手数，再逐手回退直到"含手续费也买得起"。
            // 早期版本这里是算完手数后直接 continue，导致"差一点点钱"的仓位被静默跳过，
            // 现金闲置、仓位不足，回测结果失真。
            double budget = Math.min(perStock, cash.value);
            long shares = (long) Math.floor(budget / price / LOT_SIZE) * LOT_SIZE;
            while (shares > 0 && shares * price + buyFee(shares * price, cfg) > cash.value) {
                shares -= LOT_SIZE;
            }
            if (shares <= 0) {
                continue;
            }
            double amount = price * shares;
            double fee = buyFee(amount, cfg);
            cash.value -= amount + fee;
            holdings.merge(code, shares, Long::sum);
            trades.add(new Trade(date, code, Trade.Side.BUY, round2(price), shares, round2(amount), round2(fee)));
            n++;
        }
        return n;
    }

    // ---------- 涨跌停判断 ----------

    private static double buyFee(double amount, BacktestConfig cfg) {
        return Math.max(cfg.minCommission(), amount * cfg.commissionRate());
    }

    private boolean isLimitUp(BarSeries s, Bar bar, double limitPct) {
        Double prev = prevClose(s);
        return prev != null && bar.open() >= prev * (1 + limitPct) - 1e-6;
    }

    private boolean isLimitDown(BarSeries s, Bar bar, double limitPct) {
        Double prev = prevClose(s);
        return prev != null && bar.open() <= prev * (1 - limitPct) + 1e-6;
    }

    private Double prevClose(BarSeries s) {
        Bar prev = s.nthFromEnd(2);
        return prev == null ? null : prev.close();
    }

    private double holdingsValue(MarketData data, LocalDate date, Map<String, Long> holdings) {
        double v = 0;
        for (Map.Entry<String, Long> e : holdings.entrySet()) {
            Bar last = data.series(e.getKey()).asOf(date).last();
            if (last != null) {
                v += last.close() * e.getValue();
            }
        }
        return v;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** 用可变对象在私有方法间传递现金，避免拆成十几个返回值 */
    private static final class Cash {
        double value;

        Cash(double v) {
            this.value = v;
        }
    }
}
