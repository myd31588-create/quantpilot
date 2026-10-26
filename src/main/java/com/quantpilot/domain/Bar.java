package com.quantpilot.domain;

import java.time.LocalDate;

/**
 * 一根日线。
 *
 * pe / pb 为当日快照，可能为 null（新股、亏损股）。
 * 回测统一使用后复权价，所以这里不存复权因子。
 */
public record Bar(
        LocalDate date,
        double open,
        double high,
        double low,
        double close,
        long volume,
        double amount,
        double turnoverRate,
        Double pe,
        Double pb
) {
    public boolean isValid() {
        return open > 0 && close > 0;
    }
}
