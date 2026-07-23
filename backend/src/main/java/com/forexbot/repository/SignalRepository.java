package com.forexbot.repository;

import com.forexbot.model.Signal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface SignalRepository extends JpaRepository<Signal, Long> {

    List<Signal> findTop100ByOrderByCreatedAtDesc();

    List<Signal> findBySymbolOrderByCreatedAtDesc(String symbol);

    // ── Weekly review queries ──────────────────────────────────────────────────

    List<Signal> findByCreatedAtAfter(Instant since);

    @Query("SELECT COUNT(s) FROM Signal s WHERE s.createdAt > :since AND s.direction = :direction")
    long countByDirectionSince(@Param("direction") String direction, @Param("since") Instant since);

    @Query("SELECT COUNT(s) FROM Signal s WHERE s.createdAt > :since AND s.actedOn = true")
    long countActedOnSince(@Param("since") Instant since);

    // ── Signal history (time-range queries) ───────────────────────────────────

    @Query("SELECT s FROM Signal s WHERE s.createdAt BETWEEN :from AND :to ORDER BY s.createdAt DESC")
    Page<Signal> findByDateRange(@Param("from") Instant from, @Param("to") Instant to, Pageable pageable);

    @Query("SELECT s FROM Signal s WHERE s.symbol = :symbol AND s.createdAt BETWEEN :from AND :to ORDER BY s.createdAt DESC")
    Page<Signal> findBySymbolAndDateRange(@Param("symbol") String symbol, @Param("from") Instant from, @Param("to") Instant to, Pageable pageable);
}
