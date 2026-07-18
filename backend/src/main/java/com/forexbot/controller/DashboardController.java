package com.forexbot.controller;

import com.forexbot.model.Trade;
import com.forexbot.repository.SignalRepository;
import com.forexbot.repository.TradeRepository;
import com.forexbot.service.SignalPollerService;
import com.forexbot.service.TradeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Controller
public class DashboardController {

    private final TradeRepository tradeRepository;
    private final SignalRepository signalRepository;
    private final SignalPollerService pollerService;
    private final TradeService tradeService;
    private final WebClient mt5Client;

    public DashboardController(
            TradeRepository tradeRepository,
            SignalRepository signalRepository,
            SignalPollerService pollerService,
            TradeService tradeService,
            @Qualifier("mt5WebClient") WebClient mt5Client
    ) {
        this.tradeRepository  = tradeRepository;
        this.signalRepository = signalRepository;
        this.pollerService    = pollerService;
        this.tradeService     = tradeService;
        this.mt5Client        = mt5Client;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        Map<?, ?> accountInfo = null;
        try {
            accountInfo = mt5Client.get()
                    .uri("/account")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
            log.debug("MT5 account info fetched: login={}", accountInfo != null ? accountInfo.get("login") : "null");
        } catch (WebClientRequestException e) {
            log.warn("MT5 bridge unreachable — dashboard will show without account info: {}", e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching MT5 account info", e);
        }

        long openCount = tradeRepository.countOpen();
        log.debug("Dashboard loaded — botEnabled={}, openTrades={}", pollerService.isEnabled(), openCount);

        model.addAttribute("account", accountInfo);
        model.addAttribute("botEnabled", pollerService.isEnabled());
        model.addAttribute("openTrades", tradeRepository.findByStatusOrderByOpenedAtDesc(Trade.TradeStatus.OPEN));
        model.addAttribute("recentTrades", tradeRepository.findRecent());
        model.addAttribute("recentSignals", signalRepository.findTop100ByOrderByCreatedAtDesc());
        model.addAttribute("totalProfit", tradeRepository.totalProfit());
        model.addAttribute("openTradeCount", openCount);
        return "dashboard/index";
    }

    @PostMapping("/bot/enable")
    public String enableBot() {
        log.info("Bot ENABLE requested via dashboard");
        pollerService.enable();
        return "redirect:/";
    }

    @PostMapping("/bot/disable")
    public String disableBot() {
        log.info("Bot DISABLE requested via dashboard");
        pollerService.disable();
        return "redirect:/";
    }

    @PostMapping("/trade/{id}/close")
    public String closeTrade(@PathVariable Long id) {
        log.info("Manual close requested for trade #{}", id);
        try {
            tradeService.closeTrade(id);
            log.info("Trade #{} closed successfully", id);
        } catch (Exception e) {
            log.error("Failed to close trade #{}: {}", id, e.getMessage());
        }
        return "redirect:/";
    }
}
