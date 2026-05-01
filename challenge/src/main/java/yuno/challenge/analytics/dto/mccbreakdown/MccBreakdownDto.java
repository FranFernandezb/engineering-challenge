package yuno.challenge.analytics.dto.mccbreakdown;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class MccBreakdownDto {
    private final String mcc;
    private final String mccDescription;
    private final long chargebackCount;
    private final BigDecimal totalAmount;
}
