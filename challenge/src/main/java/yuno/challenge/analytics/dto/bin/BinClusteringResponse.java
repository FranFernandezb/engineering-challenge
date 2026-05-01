package yuno.challenge.analytics.dto.bin;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BinClusteringResponse {
    private final LocalDate from;
    private final LocalDate to;
    private final List<BinStat> bins;
    private final List<CountryStat> countries;

    @Getter
    @Builder
    public static class BinStat {
        private final String cardBin;
        private final String issuerCountry;
        private final long chargebackCount;
        private final long transactionCount;
        private final BigDecimal ratePercent;
        private final BigDecimal totalAmount;
    }

    @Getter
    @Builder
    public static class CountryStat {
        private final String issuerCountry;
        private final long chargebackCount;
        private final long transactionCount;
        private final BigDecimal ratePercent;
        private final BigDecimal totalAmount;
        private final BigDecimal sharePercent;
    }
}

