package com.quantpilot.factor;

import com.quantpilot.domain.Bar;
import com.quantpilot.domain.BarSeries;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactorTest {

    @Test
    void 前视偏差_追加未来数据不能改变当天因子值() {
        LocalDate d0 = LocalDate.of(2024, 1, 1);
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            bars.add(bar(d0.plusDays(i), 10 + i, 20.0));
        }
        LocalDate asOf = d0.plusDays(24);

        double before = Factors.momentum20().value(new BarSeries("A", bars), asOf);

        // 追加 5 根"未来"的极端数据：价格翻 10 倍
        List<Bar> withFuture = new ArrayList<>(bars);
        for (int i = 30; i < 35; i++) {
            withFuture.add(bar(d0.plusDays(i), 300, 20.0));
        }
        double after = Factors.momentum20().value(new BarSeries("A", withFuture), asOf);

        assertEquals(before, after, 1e-12, "asOf 截断后，未来数据不应影响因子值");
    }

    @Test
    void 动量因子按收盘价计算() {
        LocalDate d0 = LocalDate.of(2024, 1, 1);
        List<Bar> bars = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            bars.add(bar(d0.plusDays(i), 10 + i, 20.0));
        }
        BarSeries s = new BarSeries("A", bars);
        LocalDate asOf = d0.plusDays(24);

        double expected = s.nthFromEnd(1).close() / s.nthFromEnd(21).close() - 1;
        assertEquals(expected, Factors.momentum20().value(s, asOf), 1e-12);
    }

    @Test
    void 波动率为负向因子_换手率越低越好() {
        assertTrue(!Factors.volatility20().higherIsBetter());
        assertTrue(!Factors.turnover20().higherIsBetter());
        assertTrue(Factors.momentum20().higherIsBetter());
        assertTrue(Factors.ep().higherIsBetter());
    }

    @Test
    void 数据不足时因子返回NaN而不是抛异常() {
        BarSeries s = new BarSeries("A", List.of(bar(LocalDate.of(2024, 1, 1), 10, 20.0)));
        double v = Factors.momentum20().value(s, LocalDate.of(2024, 1, 1));
        assertTrue(Double.isNaN(v));
    }

    private static Bar bar(LocalDate date, double close, double pe) {
        return new Bar(date, close, close * 1.01, close * 0.99, close,
                1_000_000, 5000, 1.5, pe, 2.0);
    }
}
