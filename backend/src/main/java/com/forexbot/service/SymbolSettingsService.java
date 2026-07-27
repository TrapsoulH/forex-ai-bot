package com.forexbot.service;

import com.forexbot.config.BotProperties;
import com.forexbot.model.SymbolSettings;
import com.forexbot.repository.SymbolSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SymbolSettingsService {

    private final SymbolSettingsRepository repo;
    private final BotProperties botProperties;

    /**
     * Returns per-symbol settings for the given symbol.
     * If no DB row exists, creates one from global BotProperties defaults and persists it.
     * This means once a symbol is first seen, it always has an editable row in the UI.
     */
    public SymbolSettings getOrCreate(String symbol) {
        return repo.findBySymbol(symbol).orElseGet(() -> {
            log.info("No symbol settings found for {} — seeding from global defaults", symbol);
            SymbolSettings s = SymbolSettings.builder()
                    .symbol(symbol)
                    .slPips(BigDecimal.valueOf(botProperties.getSlPips()))
                    .tpPips(BigDecimal.valueOf(botProperties.getTpPips()))
                    .slAtrMult(new BigDecimal("1.50"))
                    .tpAtrMult(new BigDecimal("4.50"))
                    .volume(BigDecimal.valueOf(botProperties.getDefaultVolume()))
                    .enabled(true)
                    .build();
            return repo.save(s);
        });
    }

    /** All symbol settings rows ordered by symbol name. */
    public List<SymbolSettings> findAll() {
        return repo.findAllOrdered();
    }

    /**
     * Persist changes from the bot settings form (ATR-multiplier-based UI).
     * slAtrMult/tpAtrMult are the primary risk controls in Strategy V2.
     * pip-based fields are kept as fallback but not updated here.
     */
    public SymbolSettings save(String symbol, BigDecimal slAtrMult, BigDecimal tpAtrMult,
                               BigDecimal volume, boolean enabled) {
        SymbolSettings s = getOrCreate(symbol);
        s.setSlAtrMult(slAtrMult);
        s.setTpAtrMult(tpAtrMult);
        s.setVolume(volume);
        s.setEnabled(enabled);
        SymbolSettings saved = repo.save(s);
        log.info("Symbol settings updated | symbol={} slMult={}×ATR tpMult={}×ATR vol={} enabled={}",
                symbol, slAtrMult, tpAtrMult, volume, enabled);
        return saved;
    }
}
