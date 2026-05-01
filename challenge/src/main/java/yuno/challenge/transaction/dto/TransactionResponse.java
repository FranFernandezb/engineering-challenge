package yuno.challenge.transaction.dto;

import lombok.Builder;
import lombok.Getter;
import yuno.challenge.transaction.model.Transaction;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Builder
public class TransactionResponse {

    private final String transactionId;
    private final String merchantId;
    private final String merchantName;
    private final String mcc;
    private final BigDecimal amount;
    private final String currency;
    private final LocalDate transactionDate;
    private final String cardBin;
    private final String cardLastFour;
    private final String cardType;
    private final String issuerCountry;
    private final String processorName;
    private final String status;
    private final Instant createdAt;

    public static TransactionResponse from(Transaction t) {
        return TransactionResponse.builder()
                .transactionId(t.getTransactionId())
                .merchantId(t.getMerchantId())
                .merchantName(t.getMerchantName())
                .mcc(t.getMcc())
                .amount(t.getAmount())
                .currency(t.getCurrency())
                .transactionDate(t.getTransactionDate())
                .cardBin(t.getCardBin())
                .cardLastFour(t.getCardLastFour())
                .cardType(t.getCardType())
                .issuerCountry(t.getIssuerCountry())
                .processorName(t.getProcessorName())
                .status(t.getStatus())
                .createdAt(t.getCreatedAt())
                .build();
    }
}
