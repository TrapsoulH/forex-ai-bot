package com.forexbot.controller;

import com.forexbot.model.Signal;
import com.forexbot.model.Trade;
import com.forexbot.repository.SignalRepository;
import com.forexbot.repository.TradeRepository;
import com.forexbot.service.SignalPollerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API — used by the dashboard JS and external clients.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ApiController {

    private final TradeRepository tradeRepository;
    private final SignalRepository signalRepository;
    private final SignalPollerService pollerService;

    @GetMapping("/status")
    public Map<String, Object> status() {
        log.debug("Status check requested");
        return Map.of(
                "botEnabled", pollerService.isEnabled(),
                "openTrades", tradeRepository.countOpen(),
                "totalProfit", tradeRepository.totalProfit()
        );
    }

    @GetMapping("/trades")
    public List<Trade> trades(@RequestParam(required = false) String status) {
        if (status != null) {
            log.debug("Fetching trades with status={}", status);
            return tradeRepository.findByStatusOrderByOpenedAtDesc(Trade.TradeStatus.valueOf(status.toUpperCase()));
        }
        log.debug("Fetching recent trades");
        return tradeRepository.findRecent();
    }

    @GetMapping("/signals")
    public List<Signal> signals(@RequestParam(required = false) String symbol) {
        if (symbol != null) {
            log.debug("Fetching signals for symbol={}", symbol);
            return signalRepository.findBySymbolOrderByCreatedAtDesc(symbol.toUpperCase());
        }
        log.debug("Fetching recent signals");
        return signalRepository.findTop100ByOrderByCreatedAtDesc();
    }

    @PostMapping("/bot/enable")
    public ResponseEntity<Map<String, Object>> enableBot() {
        log.info("Bot ENABLE requested via API");
        pollerService.enable();
        return ResponseEntity.ok(Map.of("enabled", true));
    }

    @PostMapping("/bot/disable")
    public ResponseEntity<Map<String, Object>> disableBot() {
        log.info("Bot DISABLE requested via API");
        pollerService.disable();
        return ResponseEntity.ok(Map.of("enabled", false));
    }
}
