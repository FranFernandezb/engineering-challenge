package yuno.challenge.chargeback.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "chargebacks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Chargeback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chargeback_id", nullable = false, unique = true)
    private String chargebackId;

    @Column(name = "transaction_id", nullable = false)
    private String transactionId;

    @Column(name = "processor_name", nullable = false)
    private String processorName;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "reason_code", nullable = false)
    private String reasonCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason_category")
    private ReasonCategory reasonCategory;

    @Column(name = "chargeback_date", nullable = false)
    private LocalDate chargebackDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ChargebackStatus status = ChargebackStatus.RECEIVED;

    @Column(name = "card_bin", nullable = false, length = 8)
    private String cardBin;

    @Column(name = "issuer_country", nullable = false, length = 2)
    private String issuerCountry;

    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "mcc", length = 4)
    private String mcc;

    @Column(name = "original_transaction_date")
    private LocalDate originalTransactionDate;

    @Column(name = "original_amount", precision = 15, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(name = "ingest_source")
    private IngestSource ingestSource;

    @Column(name = "idempotency_key", unique = true)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
