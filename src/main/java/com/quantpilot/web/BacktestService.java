package com.quantpilot.web;

import com.quantpilot.audit.AuditReport;
import com.quantpilot.audit.StrategyAuditor;
import com.quantpilot.backtest.BacktestConfig;
import com.quantpilot.backtest.BacktestEngine;
import com.quantpilot.backtest.BacktestResult;
import com.quantpilot.data.SyntheticMarketDataProvider;
import com.quantpilot.domain.Bar;
import com.quantpilot.domain.MarketData;
import com.quantpilot.domain.StockInfo;
import com.quantpilot.factor.Factors;
import com.quantpilot.strategy.Selector;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 回测服务：启动时跑一次，结果缓存住。
 *
 * MVP 阶段数据不变，所以算一次就够；接真实数据后要改成定时重算 + 缓存失效。
 */
@Service
public class BacktestService {

    private final MarketData data;
    private final BacktestResult result;
    private final List<Selector.WeightedFactor> weights;
    private final LocalDate start;
    private final Selector selector = new Selector();
    private final BacktestConfig config;
    private volatile AuditReport audit;

    public BacktestService() {
        this.data = SyntheticMarketDataProvider.defaults().load();
        this.weights = List.of(
                new Selector.WeightedFactor(Factors.momentum20(), 0.25),
                new Selector.WeightedFactor(Factors.momentum60(), 0.25),
                new Selector.WeightedFactor(Factors.volatility20(), 0.20),
                new Selector.WeightedFactor(Factors.turnover20(), 0.10),
                new Selector.WeightedFactor(Factors.ep(), 0.10),
                new Selector.WeightedFactor(Factors.bp(), 0.10)
        );
        this.start = LocalDate.of(2022, 2, 1);
        this.config = BacktestConfig.defaults(start, data.lastDate());
        this.result = new BacktestEngine().run(data, selector, weights, config);
    }

    /** 体检耗时较长（几十次回测），首次调用时算一次并缓存 */
    public synchronized AuditReport audit() {
        if (audit == null) {
            audit = new StrategyAuditor(data, selector, weights, config, 60).audit();
        }
        return audit;
    }

    public BacktestResult result() {
        return result;
    }

    public MarketData data() {
        return data;
    }

    public List<Selector.WeightedFactor> weights() {
        return weights;
    }

    public LocalDate start() {
        return start;
    }

    public String strategyText() {
        StringBuilder sb = new StringBuilder();
        for (Selector.WeightedFactor w : weights) {
            if (!sb.isEmpty()) {
                sb.append("  ·  ");
            }
            sb.append(w.factor().name()).append(' ').append(String.format("%.2f", w.weight()));
        }
        return sb.toString();
    }

    /** 最新一期持仓明细，给页面表格用 */
    public List<HoldingRow> latestHoldings() {
        if (result.rebalances().isEmpty()) {
            return List.of();
        }
        List<String> picks = result.rebalances().get(result.rebalances().size() - 1).picked();
        List<HoldingRow> rows = new ArrayList<>();
        for (String code : picks) {
            StockInfo info = data.stock(code);
            Bar last = data.series(code).asOf(data.lastDate()).last();
            Bar before = data.series(code).asOf(data.lastDate()).nthFromEnd(21);
            double ret20 = (last != null && before != null && before.close() > 0)
                    ? last.close() / before.close() - 1 : 0;
            rows.add(new HoldingRow(
                    code,
                    info == null ? "" : info.name(),
                    info == null ? "" : info.industry(),
                    last == null ? 0 : last.close(),
                    ret20,
                    last == null ? 0 : last.amount()
            ));
        }
        return rows;
    }

    public record HoldingRow(String code, String name, String industry,
                             double close, double ret20, double amount) {
    }
}
