package com.forexbot.repository;

import com.forexbot.model.BotConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BotConfigRepository extends JpaRepository<BotConfig, Long> {
    Optional<BotConfig> findByConfigKey(String configKey);
}
