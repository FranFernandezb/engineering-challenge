package yuno.challenge.analytics.dto.reasoncode;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReasonCodeBreakdownResponse {
    private final LocalDate from;
    private final LocalDate to;
    private final long totalChargebacks;
    private final List<ReasonStat> reasonCodes;

    @Getter
    @Builder
    public static class ReasonStat {
        private final String reasonCode;
        private final String reasonCategory;
        private final long chargebackCount;
        private final BigDecimal totalAmount;
        private final BigDecimal sharePercent;
    }
}
