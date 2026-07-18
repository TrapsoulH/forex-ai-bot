package com.forexbot.repository;

import com.forexbot.model.Signal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SignalRepository extends JpaRepository<Signal, Long> {

    List<Signal> findTop100ByOrderByCreatedAtDesc();

    List<Signal> findBySymbolOrderByCreatedAtDesc(String symbol);
}
