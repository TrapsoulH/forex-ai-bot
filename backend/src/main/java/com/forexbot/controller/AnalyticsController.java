package com.forexbot.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forexbot.model.Trade;
import com.forexbot.repository.TradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Serves the /analytics page — equity curve, win rate trend, confidence scatter.
 *
 * All chart data is serialised to JSON strings here so the template can embed
 * them as JavaScript variables using Thymeleaf's [[${...}]] inlining.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class AnalyticsController {

    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("dd MMM").withZone(ZoneId.systemDefault());

    private final TradeRepository tradeRepository;
    private final ObjectMapper    objectMapper;

    @GetMapping("/analytics")
    public String analytics(Model model) throws JsonProcessingException {
        List<Trade> closed = tradeRepository.findAllClosedOrdered();

        // ── Summary stats ────────────────────────────────────────────────────
        long total  = closed.size();
        long wins   = closed.stream()
                .filter(t -> t.getProfit() != null && t.getProfit().compareTo(BigDecimal.ZERO) > 0)
                .count();
        long losses = closed.stream()
                .filter(t -> t.getProfit() != null && t.getProfit().compareTo(BigDecimal.ZERO) < 0)
                .count();

        BigDecimal totalPnl = closed.stream()
                .filter(t -> t.getProfit() != null)
                .map(Trade::getProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal avgWin = closed.stream()
                .filter(t -> t.getProfit() != null && t.getProfit().compareTo(BigDecimal.ZERO) > 0)
                .map(Trade::getProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(wins > 0 ? BigDecimal.valueOf(wins) : BigDecimal.ONE, 2, RoundingMode.HALF_UP);

        BigDecimal avgLoss = closed.stream()
                .filter(t -> t.getProfit() != null && t.getProfit().compareTo(BigDecimal.ZERO) < 0)
                .map(Trade::getProfit)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(losses > 0 ? BigDecimal.valueOf(losses) : BigDecimal.ONE, 2, RoundingMode.HALF_UP);

        double winRate = total > 0 ? (double) wins / total * 100.0 : 0.0;

        // ── Equity curve (cumulative P&L by day) ─────────────────────────────
        // Group profits by day label, then compute running cumulative sum
        LinkedHashMap<String, BigDecimal> dailyPnl = new LinkedHashMap<>();
        for (Trade t : closed) {
            if (t.getProfit() == null || t.getClosedAt() == null) continue;
            String day = DAY_FMT.format(t.getClosedAt());
            dailyPnl.merge(day, t.getProfit(), BigDecimal::add);
        }

        List<String>    equityLabels = new ArrayList<>();
        List<BigDecimal> equityValues = new ArrayList<>();
        BigDecimal running = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> e : dailyPnl.entrySet()) {
            running = running.add(e.getValue());
            equityLabels.add(e.getKey());
            equityValues.add(running.setScale(2, RoundingMode.HALF_UP));
        }

        // ── Daily win rate trend ──────────────────────────────────────────────
        LinkedHashMap<String, int[]> dailyWR = new LinkedHashMap<>(); // day → [wins, total]
        for (Trade t : closed) {
            if (t.getClosedAt() == null) continue;
            String day = DAY_FMT.format(t.getClosedAt());
            dailyWR.computeIfAbsent(day, k -> new int[]{0, 0});
            dailyWR.get(day)[1]++;
            if (t.getProfit() != null && t.getProfit().compareTo(BigDecimal.ZERO) > 0) {
                dailyWR.get(day)[0]++;
            }
        }

        List<String> wrLabels = new ArrayList<>(dailyWR.keySet());
        List<Double> wrValues = new ArrayList<>();
        for (int[] v : dailyWR.values()) {
            wrValues.add(v[1] > 0 ? Math.round((double) v[0] / v[1] * 1000.0) / 10.0 : 0.0);
        }

        // ── Confidence scatter (x = confidence %, y = profit) ────────────────
        List<Map<String, Object>> scatterData = new ArrayList<>();
        for (Trade t : closed) {
            if (t.getProfit() == null || t.getSignalConfidence() == null) continue;
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("x", t.getSignalConfidence()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(1, RoundingMode.HALF_UP));
            point.put("y", t.getProfit().setScale(2, RoundingMode.HALF_UP));
            point.put("symbol", t.getSymbol());
            point.put("win", t.getProfit().compareTo(BigDecimal.ZERO) > 0);
            scatterData.add(point);
        }

        // ── Model ─────────────────────────────────────────────────────────────
        model.addAttribute("hasData",      !closed.isEmpty());
        model.addAttribute("totalTrades",  total);
        model.addAttribute("winRate",      String.format("%.1f", winRate));
        model.addAttribute("totalPnl",     totalPnl.setScale(2, RoundingMode.HALF_UP));
        model.addAttribute("pnlPositive",  totalPnl.compareTo(BigDecimal.ZERO) >= 0);
        model.addAttribute("avgWin",       avgWin);
        model.addAttribute("avgLoss",      avgLoss);
        model.addAttribute("winCount",     wins);
        model.addAttribute("lossCount",    losses);

        // Serialise chart data to JSON for Thymeleaf JS inlining
        model.addAttribute("equityLabelsJson", objectMapper.writeValueAsString(equityLabels));
        model.addAttribute("equityValuesJson", objectMapper.writeValueAsString(equityValues));
        model.addAttribute("wrLabelsJson",     objectMapper.writeValueAsString(wrLabels));
        model.addAttribute("wrValuesJson",     objectMapper.writeValueAsString(wrValues));
        model.addAttribute("scatterJson",      objectMapper.writeValueAsString(scatterData));

        return "analytics/index";
    }
}
