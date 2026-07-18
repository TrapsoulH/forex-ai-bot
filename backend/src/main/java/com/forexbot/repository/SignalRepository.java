package com.forexbot.repository;

import com.forexbot.model.Signal;
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
}
