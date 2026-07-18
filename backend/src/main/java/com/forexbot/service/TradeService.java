package com.forexbot.service;

import com.forexbot.config.BotProperties;
import com.forexbot.model.Trade;
import com.forexbot.repository.TradeRepository;
import com.forexbot.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Service
public class TradeService {

    private final BotProperties  botProperties;
    private final TradeRepository tradeRepository;
    private final UserRepository  userRepository;
    private final EmailService    emailService;
    private final WebClient       mt5Client;

    public TradeService(
            BotProperties botProperties,
            TradeRepository tradeRepository,
            UserRepository userRepository,
            EmailService emailService,
            @Qualifier("mt5WebClient") WebClient mt5Client
    ) {
        this.botProperties   = botProperties;
        this.tradeRepository = tradeRepository;
        this.userRepository  = userRepository;
        this.emailService    = emailService;
        this.mt5Client       = mt5Client;
    }

    public Trade openTrade(String symbol, String direction, BigDecimal confidence, Long signalId) {
        log.info("Opening trade | symbol={} direction={} volume={} paper={} signalId={}",
                symbol, direction, botProperties.getDefaultVolume(), botProperties.isPaperTrading(), signalId);

        Map<String, Object> body = Map.of(
                "symbol", symbol,
                "direction", direction,
                "volume", botProperties.getDefaultVolume(),
                "sl_pips", botProperties.getSlPips(),
                "tp_pips", botProperties.getTpPips()
        );

        Map<?, ?> response = null;
        try {
            response = mt5Client.post()
                    .uri("/trade/open")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            log.debug("MT5 bridge response: {}", response);
        } catch (WebClientResponseException e) {
            log.error("MT5 bridge rejected trade open | status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Failed to send trade open request to MT5 bridge: {}", e.getMessage());
        }

        Long ticket = null;
        BigDecimal openPrice = null;
        if (response != null) {
            Object t = response.get("order_id");
            if (t instanceof Number n) ticket = n.longValue();
            Object p = response.get("price");
            if (p instanceof Number n) openPrice = BigDecimal.valueOf(n.doubleValue());
        }

        Trade trade = Trade.builder()
                .symbol(symbol)
                .direction(Trade.Direction.valueOf(direction))
                .volume(BigDecimal.valueOf(botProperties.getDefaultVolume()))
                .openPrice(openPrice)
                .status(Trade.TradeStatus.OPEN)
                .mt5Ticket(ticket)
                .signalConfidence(confidence)
                .paperTrade(botProperties.isPaperTrading())
                .build();

        Trade saved = tradeRepository.save(trade);
        log.info("Trade saved | id={} symbol={} direction={} ticket={} price={}",
                saved.getId(), symbol, direction, ticket, openPrice);

        // Notify all users — fire-and-forget so email failure never affects trade flow
        try {
            userRepository.findAll().stream()
                .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                .forEach(u -> emailService.sendTradeOpened(u.getEmail(), saved));
        } catch (Exception e) {
            log.error("Failed to send trade notification emails: {}", e.getMessage());
        }

        return saved;
    }

    public Trade closeTrade(Long tradeId) {
        Trade trade = tradeRepository.findById(tradeId)
                .orElseThrow(() -> new IllegalArgumentException("Trade not found: " + tradeId));

        log.info("Closing trade | id={} symbol={} direction={} ticket={}",
                trade.getId(), trade.getSymbol(), trade.getDirection(), trade.getMt5Ticket());

        if (trade.getMt5Ticket() != null) {
            try {
                Map<?, ?> response = mt5Client.post()
                        .uri("/trade/close")
                        .bodyValue(Map.of("ticket", trade.getMt5Ticket()))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .timeout(Duration.ofSeconds(10))
                        .block();
                log.debug("MT5 bridge close response: {}", response);
            } catch (WebClientResponseException e) {
                log.error("MT5 bridge rejected trade close | status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
            } catch (Exception e) {
                log.error("Failed to send trade close request to MT5 bridge: {}", e.getMessage());
            }
        } else {
            log.warn("Trade #{} has no MT5 ticket — closing in DB only", tradeId);
        }

        trade.setStatus(Trade.TradeStatus.CLOSED);
        trade.setClosedAt(Instant.now());
        Trade saved = tradeRepository.save(trade);
        log.info("Trade #{} marked CLOSED in DB", saved.getId());
        return saved;
    }
}
