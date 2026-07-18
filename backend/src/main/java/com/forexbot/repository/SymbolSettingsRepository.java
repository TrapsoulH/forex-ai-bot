package com.forexbot.repository;

import com.forexbot.model.SymbolSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SymbolSettingsRepository extends JpaRepository<SymbolSettings, Long> {

    Optional<SymbolSettings> findBySymbol(String symbol);

    /** Returns all symbols ordered alphabetically for consistent UI display. */
    @Query("SELECT s FROM SymbolSettings s ORDER BY s.symbol ASC")
    List<SymbolSettings> findAllOrdered();
}
