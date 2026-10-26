package com.quantpilot.web;

import com.quantpilot.backtest.BacktestResult;

import java.util.List;

/**
 * 服务端渲染 SVG 净值曲线。
 *
 * 为什么不用 ECharts：
 *  1. CDN 在国内不稳定，弱网环境可能加载不出来
 *  2. 页面要能离线打开，不依赖任何外部资源
 *  3. 一条折线，手画 SVG 比引 1MB 的图表库划算
 *
 * 颜色按 A 股习惯：红涨绿跌（和欧美相反，别写反了）。
 */
public final class ChartRenderer {

    private static final String UP = "#c62828";
    private static final String DOWN = "#0a7b3e";

    private ChartRenderer() {
    }

    public static String navChart(List<BacktestResult.NavPoint> nav, int width, int height) {
        if (nav == null || nav.size() < 2) {
            return "<svg width=\"" + width + "\" height=\"" + height + "\"></svg>";
        }

        int padLeft = 56, padRight = 16, padTop = 16, padBottom = 28;
        int plotW = width - padLeft - padRight;
        int plotH = height - padTop - padBottom;

        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (BacktestResult.NavPoint p : nav) {
            min = Math.min(min, p.nav());
            max = Math.max(max, p.nav());
        }
        // 上下各留 8% 余量，曲线不贴边
        double margin = Math.max(0.02, (max - min) * 0.08);
        min -= margin;
        max += margin;

        boolean up = nav.get(nav.size() - 1).nav() >= nav.get(0).nav();
        String color = up ? UP : DOWN;

        StringBuilder sb = new StringBuilder();
        sb.append("<svg viewBox=\"0 0 ").append(width).append(' ').append(height)
                .append("\" width=\"100%\" preserveAspectRatio=\"none\" xmlns=\"http://www.w3.org/2000/svg\">");

        // 网格 + Y 轴刻度
        for (int i = 0; i <= 4; i++) {
            double v = min + (max - min) * i / 4.0;
            int y = padTop + (int) Math.round((max - v) / (max - min) * plotH);
            sb.append(String.format("<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#eef0f4\" stroke-width=\"1\"/>",
                    padLeft, y, padLeft + plotW, y));
            sb.append(String.format("<text x=\"%d\" y=\"%d\" font-size=\"11\" fill=\"#9aa3b2\" text-anchor=\"end\">%.2f</text>",
                    padLeft - 8, y + 4, v));
        }

        // 1.0 基准线
        if (min < 1.0 && max > 1.0) {
            int y = padTop + (int) Math.round((max - 1.0) / (max - min) * plotH);
            sb.append(String.format("<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"#c9ced8\" stroke-width=\"1\" stroke-dasharray=\"4 4\"/>",
                    padLeft, y, padLeft + plotW, y));
        }

        // 折线
        StringBuilder pts = new StringBuilder();
        for (int i = 0; i < nav.size(); i++) {
            double x = padLeft + (double) i / (nav.size() - 1) * plotW;
            double y = padTop + (max - nav.get(i).nav()) / (max - min) * plotH;
            pts.append(String.format("%.1f,%.1f ", x, y));
        }

        // 面积
        sb.append(String.format("<polygon points=\"%d,%d %s%d,%d\" fill=\"%s\" opacity=\"0.08\"/>",
                padLeft, padTop + plotH, pts, padLeft + plotW, padTop + plotH, color));

        sb.append(String.format("<polyline points=\"%s\" fill=\"none\" stroke=\"%s\" stroke-width=\"1.8\"/>",
                pts.toString().trim(), color));

        // X 轴首尾日期
        sb.append(String.format("<text x=\"%d\" y=\"%d\" font-size=\"11\" fill=\"#9aa3b2\" text-anchor=\"start\">%s</text>",
                padLeft, height - 8, nav.get(0).date()));
        sb.append(String.format("<text x=\"%d\" y=\"%d\" font-size=\"11\" fill=\"#9aa3b2\" text-anchor=\"end\">%s</text>",
                padLeft + plotW, height - 8, nav.get(nav.size() - 1).date()));

        sb.append("</svg>");
        return sb.toString();
    }
}
