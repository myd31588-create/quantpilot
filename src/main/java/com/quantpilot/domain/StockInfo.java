package com.quantpilot.domain;

import java.time.LocalDate;

/**
 * 股票静态信息。
 *
 * listedDate 用来做「上市满 N 天」的过滤；
 * 退市股票也要保留（避免幸存者偏差），用 delistedDate 标记。
 */
public record StockInfo(
        String code,
        String name,
        String industry,
        boolean st,
        LocalDate listedDate,
        LocalDate delistedDate
) {
    public boolean isListedOn(LocalDate date) {
        if (listedDate != null && date.isBefore(listedDate)) {
            return false;
        }
        return delistedDate == null || !date.isAfter(delistedDate);
    }

    /** 上市是否已满 minDays 个自然日（近似即可） */
    public boolean listedAtLeast(LocalDate date, int minDays) {
        return listedDate != null && !date.isBefore(listedDate.plusDays(minDays));
    }
}
