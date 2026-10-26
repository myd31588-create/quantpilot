package com.quantpilot.audit;

import com.quantpilot.domain.MarketData;
import com.quantpilot.domain.StockInfo;
import com.quantpilot.strategy.Selector;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 随机选股对照：用来回答"你的策略是不是只是运气好"。
 *
 * 做法：保持调仓频率、股票池、过滤条件完全一致，只把"选 Top N"换成"随机选 N 只"。
 * 跑 N 次得到随机策略的收益分布，再看真实策略落在分布的什么位置。
 *
 * 这就是蒙特卡洛置换检验（permutation test）的简化版。
 */
public class RandomSelector extends Selector {

    private final long seed;
    private final LocalDate universeDate;

    public RandomSelector(long seed) {
        this.seed = seed;
        this.universeDate = null;
    }

    @Override
    public List<String> select(MarketData data, LocalDate asOf, List<WeightedFactor> weights, int topN) {
        List<String> eligible = new ArrayList<>();
        for (String code : data.codes()) {
            StockInfo info = data.stock(code);
            if (info == null || !info.isListedOn(asOf)) {
                continue;
            }
            if (!info.listedAtLeast(asOf, 60)) {
                continue;
            }
            var series = data.series(code).asOf(asOf);
            var last = series.last();
            if (last == null || !last.date().equals(asOf) || last.amount() < 500) {
                continue;
            }
            eligible.add(code);
        }
        if (eligible.isEmpty()) {
            return List.of();
        }
        // 用 asOf 的日期参与种子：保证同一天的选择可复现
        Random rnd = new Random(seed + asOf.toEpochDay());
        Collections.shuffle(eligible, rnd);
        return eligible.subList(0, Math.min(topN, eligible.size()));
    }
}
