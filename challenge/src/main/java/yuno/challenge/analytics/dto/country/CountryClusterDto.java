package yuno.challenge.analytics.dto.country;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
class CountryClusterDto {
    private final String issuerCountry;
    private final long chargebackCount;
    private final BigDecimal totalAmount;
    private final BigDecimal sharePercent;
}
