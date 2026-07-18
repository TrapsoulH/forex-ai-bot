package com.forexbot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "bot")
public class BotProperties {
    private boolean paperTrading = true;
    private int scanIntervalSeconds = 60;
    private double defaultVolume = 0.01;
    private double slPips = 30.0;
    private double tpPips = 60.0;
    private int maxOpenTrades = 3;
    private List<String> symbols = List.of("EURUSD", "GBPUSD", "USDJPY", "AUDUSD");
}
