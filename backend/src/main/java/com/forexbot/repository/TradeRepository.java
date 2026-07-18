package com.forexbot.repository;

import com.forexbot.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByStatusOrderByOpenedAtDesc(Trade.TradeStatus status);

    List<Trade> findBySymbolOrderByOpenedAtDesc(String symbol);

    @Query("SELECT t FROM Trade t ORDER BY t.openedAt DESC LIMIT 50")
    List<Trade> findRecent();

    @Query("SELECT SUM(t.profit) FROM Trade t WHERE t.status = 'CLOSED'")
    BigDecimal totalProfit();

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.status = 'OPEN'")
    long countOpen();

    // ── Analytics queries ──────────────────────────────────────────────────────

    /** All closed trades sorted oldest-first — for equity curve and scatter chart. */
    @Query("SELECT t FROM Trade t WHERE t.status = 'CLOSED' ORDER BY t.closedAt ASC")
    List<Trade> findAllClosedOrdered();

    // ── Weekly review queries ──────────────────────────────────────────────────

    @Query("SELECT t FROM Trade t WHERE t.openedAt > :since ORDER BY t.openedAt DESC")
    List<Trade> findSince(@Param("since") Instant since);

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.openedAt > :since AND t.status = 'CLOSED' AND t.profit > 0")
    long countWinsSince(@Param("since") Instant since);

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.openedAt > :since AND t.status = 'CLOSED' AND t.profit < 0")
    long countLossesSince(@Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(t.profit), 0) FROM Trade t WHERE t.openedAt > :since AND t.status = 'CLOSED'")
    BigDecimal sumProfitSince(@Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(t.profit), 0) FROM Trade t WHERE t.openedAt > :since AND t.status = 'CLOSED' AND t.symbol = :symbol")
    BigDecimal sumProfitBySymbolSince(@Param("symbol") String symbol, @Param("since") Instant since);

    @Query("SELECT COALESCE(AVG(t.signalConfidence), 0) FROM Trade t WHERE t.openedAt > :since AND t.status = 'CLOSED' AND t.profit > 0")
    BigDecimal avgConfidenceWinsSince(@Param("since") Instant since);

    @Query("SELECT COALESCE(AVG(t.signalConfidence), 0) FROM Trade t WHERE t.openedAt > :since AND t.status = 'CLOSED' AND t.profit < 0")
    BigDecimal avgConfidenceLossesSince(@Param("since") Instant since);
}
