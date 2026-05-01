package yuno.challenge.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Single transaction ingest payload — strict schema (transactions don't suffer
 * the same processor-format heterogeneity that chargebacks do).
 */
@Data
public class TransactionIngestRequest {

    @NotBlank(message = "transactionId is required")
    private String transactionId;

    @NotBlank(message = "merchantId is required")
    private String merchantId;

    @NotBlank(message = "merchantName is required")
    private String merchantName;

    @NotBlank
    @Size(min = 4, max = 4, message = "mcc must be 4 characters")
    private String mcc;

    @NotNull
    @Positive(message = "amount must be positive")
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3, message = "currency must be ISO 4217 (3 chars)")
    private String currency;

    @NotNull
    private LocalDate transactionDate;

    @NotBlank
    @Size(min = 6, max = 8, message = "cardBin must be 6–8 digits")
    private String cardBin;

    @Size(max = 4)
    private String cardLastFour;

    private String cardType;

    @NotBlank
    @Size(min = 2, max = 2, message = "issuerCountry must be ISO 3166-1 alpha-2")
    private String issuerCountry;

    @NotBlank
    private String processorName;

    @NotBlank
    private String status;
}
