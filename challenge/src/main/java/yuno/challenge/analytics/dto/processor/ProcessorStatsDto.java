package yuno.challenge.analytics.dto.processor;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class ProcessorStatsDto {
    private final String processorName;
    private final long chargebackCount;
    private final BigDecimal totalAmount;
    private final String currency;
}