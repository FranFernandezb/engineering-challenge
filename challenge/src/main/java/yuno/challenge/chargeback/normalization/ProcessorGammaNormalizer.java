package yuno.challenge.chargeback.normalization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Normalizer for ProcessorGamma.
 * <p>
 * Notable differences:
 * <ul>
 *   <li>SCREAMING_SNAKE_CASE field names (common in CSV exports)</li>
 *   <li>Amount as decimal string with explicit currency symbol stripped</li>
 *   <li>Date as Unix epoch (seconds)</li>
 *   <li>Internal PG-xxx reason codes</li>
 *   <li>ISSUER_COUNTRY as full ISO alpha-2 but sometimes lowercase</li>
 * </ul>
 *
 * Field mapping:
 * <pre>
 *   DISPUTE_ID        → chargebackId
 *   TXN_ID            → transactionId
 *   DISPUTE_AMOUNT    → amount (e.g. "299000.00")
 *   CURRENCY          → currency
 *   DISPUTE_CODE      → reasonCode
 *   DISPUTE_EPOCH     → chargebackDate (Unix timestamp in seconds)
 *   BIN               → cardBin
 *   COUNTRY           → issuerCountry
 * </pre>
 */
@Slf4j
@Component
public class ProcessorGammaNormalizer implements ProcessorNormalizer {

    @Override
    public String processorName() {
        return "ProcessorGamma";
    }

    @Override
    public NormalizedChargebackRecord normalize(RawChargebackRecord raw) {
        Map<String, String> f = raw.getFields();
        log.debug("Normalizing ProcessorGamma record: {}", f.get("DISPUTE_ID"));

        String chargebackId  = require(f, "DISPUTE_ID");
        String transactionId = require(f, "TXN_ID");
        String amountStr     = require(f, "DISPUTE_AMOUNT");
        String currency      = require(f, "CURRENCY");
        String reasonCode    = require(f, "DISPUTE_CODE");
        String epochStr      = require(f, "DISPUTE_EPOCH");
        String cardBin       = require(f, "BIN");
        String issuerCountry = require(f, "COUNTRY");

        return NormalizedChargebackRecord.builder()
                .chargebackId(chargebackId)
                .transactionId(transactionId)
                .processorName(processorName())
                .amount(parseAmount(amountStr))
                .currency(currency.toUpperCase())
                .reasonCode(reasonCode)
                .chargebackDate(parseEpochDate(epochStr))
                .cardBin(sanitizeBin(cardBin))
                .issuerCountry(issuerCountry.toUpperCase())
                .rawPayload(raw.getRawPayload())
                .build();
    }

    private String require(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "ProcessorGamma: missing required field '" + key + "'");
        }
        return value.trim();
    }

    private BigDecimal parseAmount(String value) {
        try {
            // Strip any currency symbols (IDR, $, Rp, etc.)
            String cleaned = value.replaceAll("[^0-9.]", "").trim();
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ProcessorGamma: invalid amount: " + value);
        }
    }

    private LocalDate parseEpochDate(String epochSeconds) {
        try {
            long epoch = Long.parseLong(epochSeconds.trim());
            return Instant.ofEpochSecond(epoch).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ProcessorGamma: invalid epoch date: " + epochSeconds);
        }
    }

    private String sanitizeBin(String bin) {
        String digits = bin.replaceAll("[^0-9]", "");
        return digits.substring(0, Math.min(8, digits.length()));
    }
}
