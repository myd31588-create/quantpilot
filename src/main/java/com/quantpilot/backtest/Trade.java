package com.quantpilot.backtest;

import java.time.LocalDate;
import java.util.List;

/** 一笔成交 */
public record Trade(
        LocalDate date,
        String code,
        Side side,
        double price,
        long shares,
        double amount,
        double fee
) {
    public enum Side { BUY, SELL }
}
