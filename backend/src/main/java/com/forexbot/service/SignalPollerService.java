package com.forexbot.service;

import com.forexbot.config.BotProperties;
import com.forexbot.dto.SignalDto;
import com.forexbot.model.Signal;
import com.forexbot.repository.SignalRepository;
import com.forexbot.repository.TradeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.time.Duration;

@Slf4j
@Service
public class SignalPollerService {

    private final BotProperties botProperties;
    private final SignalRepository signalRepository;
    private final TradeRepository tradeRepository;
    private final TradeService tradeService;
    private final WebClient signalClient;

    private volatile boolean botEnabled = false;

    public SignalPollerService(
            BotProperties botProperties,
            SignalRepository signalRepository,
            TradeRepository tradeRepository,
            TradeService tradeService,
            @Qualifier("signalWebClient") WebClient signalClient
    ) {
        this.botProperties    = botProperties;
        this.signalRepository = signalRepository;
        this.tradeRepository  = tradeRepository;
        this.tradeService     = tradeService;
        this.signalClient     = signalClient;
    }

    public void enable()  { botEnabled = true;  log.info("Bot ENABLED"); }
    public void disable() { botEnabled = false; log.info("Bot DISABLED"); }
    public boolean isEnabled() { return botEnabled; }

    @Scheduled(fixedDelayString = "#{${bot.scan-interval-seconds:60} * 1000}")
    public void scan() {
        if (!botEnabled) {
            log.debug("Bot is disabled — skipping scan");
            return;
        }

        long openTrades = tradeRepository.countOpen();
        if (openTrades >= botProperties.getMaxOpenTrades()) {
            log.info("Max open trades reached ({}) — skipping scan", openTrades);
            return;
        }

        for (String symbol : botProperties.getSymbols()) {
            try {
                pollSymbol(symbol);
            } catch (Exception e) {
                log.error("Error polling signal for {}: {}", symbol, e.getMessage());
            }
        }
    }

    private void pollSymbol(String symbol) {
        SignalDto dto = signalClient.get()
                .uri("/signal/{symbol}", symbol)
                .retrieve()
                .bodyToMono(SignalDto.class)
                .timeout(Duration.ofSeconds(10))
                .block();

        if (dto == null) return;

        Signal saved = signalRepository.save(Signal.builder()
                .symbol(dto.getSymbol())
                .direction(dto.getSignal())
                .confidence(dto.getConfidence())
                .technicalSignal(dto.getTechnical())
                .mlSignal(dto.getMl())
                .mlConfidence(dto.getMlConfidence())
                .reason(dto.getReason())
                .actedOn(false)
                .build());

        log.info("[{}] Signal: {} (conf={}, reason={})",
                symbol, dto.getSignal(), dto.getConfidence(), dto.getReason());

        if (!"HOLD".equals(dto.getSignal())) {
            tradeService.openTrade(symbol, dto.getSignal(), dto.getConfidence(), saved.getId());
            saved.setActedOn(true);
            signalRepository.save(saved);
        }
    }
}
