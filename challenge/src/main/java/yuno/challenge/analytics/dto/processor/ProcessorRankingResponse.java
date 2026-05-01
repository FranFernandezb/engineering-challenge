package yuno.challenge.analytics.dto.processor;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProcessorRankingResponse {
    private final LocalDate from;
    private final LocalDate to;
    private final BigDecimal overallRatePercent;
    private final List<ProcessorStat> processors;

    @Getter
    @Builder
    public static class ProcessorStat {
        private final String processorName;
        private final long chargebackCount;
        private final long transactionCount;
        private final BigDecimal ratePercent;     // (chargebacks / transactions) * 100
        private final BigDecimal totalAmount;
    }
}

