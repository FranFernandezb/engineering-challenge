package yuno.challenge.analytics.dto.mccbreakdown;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MccBreakdownResponse {
    private final LocalDate from;
    private final LocalDate to;
    private final List<MccStat> mccs;

    @Getter
    @Builder
    public static class MccStat {
        private final String mcc;
        private final String mccDescription;
        private final long chargebackCount;
        private final long transactionCount;
        private final BigDecimal ratePercent;
        private final BigDecimal totalAmount;
    }
}
