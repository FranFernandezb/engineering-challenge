package yuno.challenge.analytics.dto.chargebackrate;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class ChargebackRateDto {
    private final LocalDate month;
    private final long chargebackCount;
    private final long transactionCount;
    private final BigDecimal ratePercent;
    private final String riskLevel;       // GREEN / AMBER / RED
}
