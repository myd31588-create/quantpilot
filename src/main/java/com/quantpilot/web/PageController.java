package com.quantpilot.web;

import com.quantpilot.backtest.BacktestResult;
import com.quantpilot.backtest.Metrics;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Controller
public class PageController {

    private final BacktestService service;

    public PageController(BacktestService service) {
        this.service = service;
    }

    @GetMapping("/")
    public String index(Model model) {
        BacktestResult r = service.result();
        Metrics m = r.metrics();

        model.addAttribute("metrics", m);
        model.addAttribute("totalReturnPct", fmt(m.totalReturn() * 100));
        model.addAttribute("annualReturnPct", fmt(m.annualizedReturn() * 100));
        model.addAttribute("volPct", fmt(m.annualizedVolatility() * 100));
        model.addAttribute("mddPct", fmt(m.maxDrawdown() * 100));
        model.addAttribute("winRatePct", fmt(m.winRate() * 100));
        model.addAttribute("sharpe", fmt(m.sharpe()));
        model.addAttribute("strategy", service.strategyText());
        model.addAttribute("start", service.start());
        model.addAttribute("end", service.data().lastDate());
        model.addAttribute("stockCount", service.data().codes().size());
        model.addAttribute("rebalanceCount", r.rebalances().size());
        model.addAttribute("tradeCount", r.trades().size());
        model.addAttribute("chart", ChartRenderer.navChart(r.nav(), 900, 320));
        model.addAttribute("holdings", service.latestHoldings());
        model.addAttribute("rebalances", tailReversed(r.rebalances(), 6));
        model.addAttribute("up", m.totalReturn() >= 0);
        return "index";
    }

    @GetMapping("/api/backtest")
    @ResponseBody
    public BacktestResult api() {
        return service.result();
    }

    /** 回测体检报告：这是本项目区别于普通回测工具的核心能力 */
    @GetMapping("/audit")
    public String auditPage(Model model) {
        model.addAttribute("report", service.audit());
        return "audit";
    }

    @GetMapping("/api/audit")
    @ResponseBody
    public com.quantpilot.audit.AuditReport audit() {
        return service.audit();
    }

    @GetMapping("/api/holdings")
    @ResponseBody
    public List<BacktestService.HoldingRow> holdings() {
        return service.latestHoldings();
    }

    private static String fmt(double v) {
        return String.format("%.2f", v);
    }

    private static List<RebalanceView> tailReversed(List<BacktestResult.Rebalance> all, int n) {
        int from = Math.max(0, all.size() - n);
        List<RebalanceView> out = new ArrayList<>();
        for (int i = all.size() - 1; i >= from; i--) {
            BacktestResult.Rebalance rb = all.get(i);
            out.add(new RebalanceView(rb.date(), rb.traded(), String.join(", ", rb.picked())));
        }
        return out;
    }

    public record RebalanceView(LocalDate date, int traded, String pickedText) {
    }
}
