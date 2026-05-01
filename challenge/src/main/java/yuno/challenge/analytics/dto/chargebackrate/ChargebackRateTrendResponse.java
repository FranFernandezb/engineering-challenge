package yuno.challenge.analytics.dto.chargebackrate;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChargebackRateTrendResponse {
    private final LocalDate from;
    private final LocalDate to;
    private final List<MonthlyRate> months;
    private final String overallRisk;
    private final String alert;

    @Getter
    @Builder
    public static class MonthlyRate {
        private final String month;          // "2025-03"
        private final long chargebackCount;
        private final long transactionCount;
        private final BigDecimal ratePercent;
        private final String riskLevel;      // GREEN / AMBER / RED
    }
}
