package com.forexbot.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "trades")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private Direction direction;

    @Column(nullable = false, precision = 10, scale = 5)
    private BigDecimal volume;

    @Column(name = "open_price", precision = 10, scale = 5)
    private BigDecimal openPrice;

    @Column(name = "close_price", precision = 10, scale = 5)
    private BigDecimal closePrice;

    @Column(name = "sl_price", precision = 10, scale = 5)
    private BigDecimal slPrice;

    @Column(name = "tp_price", precision = 10, scale = 5)
    private BigDecimal tpPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal profit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TradeStatus status;

    @Column(name = "mt5_ticket")
    private Long mt5Ticket;

    @Column(name = "signal_confidence", precision = 5, scale = 4)
    private BigDecimal signalConfidence;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "paper_trade", nullable = false)
    private boolean paperTrade;

    @PrePersist
    protected void onCreate() {
        openedAt = Instant.now();
        if (status == null) status = TradeStatus.OPEN;
    }

    public enum Direction { BUY, SELL }
    public enum TradeStatus { OPEN, CLOSED, CANCELLED }
}
