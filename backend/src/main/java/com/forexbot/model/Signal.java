package com.forexbot.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "signals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Signal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String symbol;

    @Column(nullable = false, length = 4)
    private String direction;   // BUY | SELL | HOLD

    @Column(precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "technical_signal", length = 4)
    private String technicalSignal;

    @Column(name = "ml_signal", length = 4)
    private String mlSignal;

    @Column(name = "ml_confidence", precision = 5, scale = 4)
    private BigDecimal mlConfidence;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "acted_on")
    private boolean actedOn;

    @Column(name = "trade_id")
    private Long tradeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
