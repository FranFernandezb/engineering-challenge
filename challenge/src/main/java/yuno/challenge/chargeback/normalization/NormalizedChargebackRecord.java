package yuno.challenge.chargeback.normalization;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Unified chargeback record ready to be persisted, regardless of which
 * processor it came from.
 */
@Getter
@Builder
public class NormalizedChargebackRecord {

    private final String chargebackId;
    private final String transactionId;
    private final String processorName;
    private final BigDecimal amount;
    private final String currency;
    private final String reasonCode;
    private final LocalDate chargebackDate;
    private final String cardBin;
    private final String issuerCountry;
    private final String rawPayload;
}
