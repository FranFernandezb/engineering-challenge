package yuno.challenge.alert.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlertReport {

    private final LocalDate from;
    private final LocalDate to;

    private final long totalChargebacks;
    private final long totalTransactions;
    private final BigDecimal overallRatePercent;

    private final int totalAlerts;
    private final int criticalCount;
    private final int warningCount;
    private final int infoCount;

    /** Top-level summary of risk: GREEN / AMBER / RED */
    private final String overallRisk;

    private final List<Alert> alerts;
}
