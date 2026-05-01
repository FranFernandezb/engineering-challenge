package yuno.challenge.analytics.dto.reasoncode;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class ReasonCodeDto {
    private final String reasonCode;
    private final String reasonCategory;
    private final long chargebackCount;
    private final BigDecimal totalAmount;
    private final BigDecimal sharePercent;
}
