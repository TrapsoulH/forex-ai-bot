package com.forexbot.controller;

import com.forexbot.model.BotConfig;
import com.forexbot.model.SymbolSettings;
import com.forexbot.repository.BotConfigRepository;
import com.forexbot.service.SymbolSettingsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/settings")
public class BotSettingsController {

    // Display label + description for each key — order matters (matches the form)
    private static final Map<String, String[]> KEY_META = new LinkedHashMap<>();
    static {
        KEY_META.put("paper_trading",     new String[]{"Paper trading mode",      "true = no real orders placed. Set to false only when you're ready for live trading."});
        KEY_META.put("scan_interval_sec", new String[]{"Scan interval (seconds)", "How often the signal engine is polled. Minimum 30."});
        KEY_META.put("sl_pips",           new String[]{"Stop loss (pips)",        "Stop loss distance in pips."});
        KEY_META.put("tp_pips",           new String[]{"Take profit (pips)",      "Take profit distance in pips. Default is 2:1 risk/reward vs SL."});
        KEY_META.put("default_volume",    new String[]{"Default lot size",        "Volume per trade (e.g. 0.01 = micro lot). Keep small until you're confident."});
        KEY_META.put("max_open_trades",   new String[]{"Max open trades",         "Maximum number of trades open at the same time."});
        KEY_META.put("min_ml_confidence", new String[]{"Min ML confidence",       "Minimum ML model confidence (0–1) required to act on a signal. Default 0.55."});
        KEY_META.put("symbols",           new String[]{"Symbols",                 "Comma-separated list of pairs to trade, e.g. EURUSD,GBPUSD,USDJPY,AUDUSD."});
    }

    private final BotConfigRepository   configRepository;
    private final SymbolSettingsService symbolSettingsService;

    public BotSettingsController(BotConfigRepository configRepository,
                                 SymbolSettingsService symbolSettingsService) {
        this.configRepository      = configRepository;
        this.symbolSettingsService = symbolSettingsService;
    }

    @GetMapping("/bot")
    public String botSettingsPage(Model model) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : KEY_META.keySet()) {
            values.put(key, configRepository.findByConfigKey(key)
                    .map(BotConfig::getConfigValue)
                    .orElse(""));
        }
        model.addAttribute("values", values);
        model.addAttribute("keyMeta", KEY_META);
        model.addAttribute("symbolSettings", symbolSettingsService.findAll());
        return "settings/bot";
    }

    // ── Global settings save ──────────────────────────────────────────────────

    @PostMapping("/bot")
    public String saveBotSettings(@RequestParam Map<String, String> params,
                                  RedirectAttributes redirectAttrs) {
        int saved = 0;
        for (String key : KEY_META.keySet()) {
            String val = params.getOrDefault(key, "").trim();
            if (val.isEmpty()) continue;

            BotConfig cfg = configRepository.findByConfigKey(key)
                    .orElse(BotConfig.builder().configKey(key).build());
            cfg.setConfigValue(val);
            cfg.setDescription(KEY_META.get(key)[1]);
            configRepository.save(cfg);
            saved++;
        }
        log.info("Bot settings updated ({} keys saved)", saved);
        redirectAttrs.addFlashAttribute("success", "Settings saved.");
        return "redirect:/settings/bot";
    }

    // ── Per-symbol risk save ──────────────────────────────────────────────────

    /**
     * Individual save for one symbol row.
     * The HTML form for each symbol posts to this endpoint with hidden field {@code symbol}.
     * Checkbox absence means unchecked (browser standard).
     */
    @PostMapping("/bot/symbol")
    public String saveSymbolSettings(@RequestParam String symbol,
                                     @RequestParam BigDecimal sl_pips,
                                     @RequestParam BigDecimal tp_pips,
                                     @RequestParam BigDecimal volume,
                                     @RequestParam(required = false) String enabled,
                                     RedirectAttributes redirectAttrs) {
        boolean isEnabled = "true".equalsIgnoreCase(enabled);
        symbolSettingsService.save(symbol, sl_pips, tp_pips, volume, isEnabled);
        redirectAttrs.addFlashAttribute("success", symbol + " settings saved.");
        return "redirect:/settings/bot";
    }
}
