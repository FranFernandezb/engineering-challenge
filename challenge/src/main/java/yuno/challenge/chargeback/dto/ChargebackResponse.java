package yuno.challenge.chargeback.dto;


import yuno.challenge.chargeback.model.Chargeback;
import yuno.challenge.chargeback.model.ChargebackStatus;
import yuno.challenge.chargeback.model.ReasonCategory;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class ChargebackResponse {

    private final String chargebackId;
    private final String transactionId;
    private final String processorName;
    private final BigDecimal amount;
    private final String currency;
    private final String reasonCode;
    private final ReasonCategory reasonCategory;
    private final LocalDate chargebackDate;
    private final ChargebackStatus status;
    private final String cardBin;
    private final String issuerCountry;

    // Enriched fields
    private final String merchantId;
    private final String merchantName;
    private final String mcc;
    private final LocalDate originalTransactionDate;
    private final BigDecimal originalAmount;

    private final Instant createdAt;

    public static ChargebackResponse from(Chargeback cb) {
        return ChargebackResponse.builder()
                .chargebackId(cb.getChargebackId())
                .transactionId(cb.getTransactionId())
                .processorName(cb.getProcessorName())
                .amount(cb.getAmount())
                .currency(cb.getCurrency())
                .reasonCode(cb.getReasonCode())
                .reasonCategory(cb.getReasonCategory())
                .chargebackDate(cb.getChargebackDate())
                .status(cb.getStatus())
                .cardBin(cb.getCardBin())
                .issuerCountry(cb.getIssuerCountry())
                .merchantId(cb.getMerchantId())
                .merchantName(cb.getMerchantName())
                .mcc(cb.getMcc())
                .originalTransactionDate(cb.getOriginalTransactionDate())
                .originalAmount(cb.getOriginalAmount())
                .createdAt(cb.getCreatedAt())
                .build();
    }
}
