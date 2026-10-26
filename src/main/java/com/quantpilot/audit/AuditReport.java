package com.quantpilot.audit;

import java.util.List;

/**
 * 回测体检报告。
 *
 * 和普通回测报告的区别：它不回答"赚了多少"，而是回答"这个结果值不值得信"。
 */
public record AuditReport(
        int score,
        List<Check> checks,
        double strategyReturn,
        double randomPValue,
        int permutationRuns
) {
    public enum Status {
        PASS, WARN, FAIL
    }

    public record Check(String name, Status status, String detail, String advice) {
    }

    public boolean healthy() {
        return score >= 75;
    }
}
