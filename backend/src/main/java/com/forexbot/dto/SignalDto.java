package com.forexbot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SignalDto {
    private String symbol;
    private String signal;
    private BigDecimal confidence;
    private String technical;
    private String ml;
    @JsonProperty("ml_confidence")
    private BigDecimal mlConfidence;
    private String reason;
}
