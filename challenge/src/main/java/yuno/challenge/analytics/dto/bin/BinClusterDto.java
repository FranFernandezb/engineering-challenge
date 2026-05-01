package yuno.challenge.analytics.dto.bin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class BinClusterDto {
    private final String cardBin;
    private final String issuerCountry;
    private final long chargebackCount;
    private final BigDecimal totalAmount;
}
