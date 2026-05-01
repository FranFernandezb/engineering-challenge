package yuno.challenge.alert.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * A single alert produced by the pattern-detection engine.
 * Severity levels mirror the network thresholds: GREEN / AMBER / RED, plus INFO.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Alert {

    public enum Severity { INFO, WARNING, CRITICAL }

    /** Stable identifier of the rule that fired (e.g. "OVERALL_RATE_THRESHOLD") */
    private final String ruleId;

    /** What dimension this alert is about: PROCESSOR / BIN / COUNTRY / REASON / OVERALL / MCC */
    private final String dimension;

    private final Severity severity;

    /** Human-readable, action-oriented message */
    private final String message;

    /** The metric value that triggered the rule (e.g. rate %, count, share %) */
    private final BigDecimal metricValue;

    /** The configured threshold the metric crossed */
    private final BigDecimal threshold;

    /** Free-form structured detail (e.g. {"bin": "453210", "country": "ID"}) */
    private final Map<String, Object> details;

    @Builder.Default
    private final Instant detectedAt = Instant.now();
}
