package com.forexbot.repository;

import com.forexbot.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByStatusOrderByOpenedAtDesc(Trade.TradeStatus status);

    List<Trade> findBySymbolOrderByOpenedAtDesc(String symbol);

    @Query("SELECT t FROM Trade t ORDER BY t.openedAt DESC LIMIT 50")
    List<Trade> findRecent();

    @Query("SELECT SUM(t.profit) FROM Trade t WHERE t.status = 'CLOSED'")
    java.math.BigDecimal totalProfit();

    @Query("SELECT COUNT(t) FROM Trade t WHERE t.status = 'OPEN'")
    long countOpen();
}
