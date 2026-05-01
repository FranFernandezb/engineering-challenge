package yuno.challenge.chargeback.normalization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Normalizer for ProcessorBeta.
 * <p>
 * Notable differences from ProcessorAlpha:
 * <ul>
 *   <li>Uses camelCase field names</li>
 *   <li>Amount is in cents (integer) — must divide by 100</li>
 *   <li>Uses Mastercard reason codes (4xxx series) plus internal PB-xxx codes</li>
 *   <li>Date format: MM/dd/yyyy</li>
 *   <li>BIN may be 6 or 8 digits</li>
 * </ul>
 *
 * Field mapping (ProcessorBeta → unified):
 * <pre>
 *   cbId              → chargebackId
 *   txnReference      → transactionId
 *   amountCents       → amount (divide by 100)
 *   currencyCode      → currency
 *   reasonCode        → reasonCode
 *   chargebackDate    → chargebackDate (MM/dd/yyyy)
 *   binNumber         → cardBin
 *   issuingCountry    → issuerCountry
 * </pre>
 */
@Slf4j
@Component
public class ProcessorBetaNormalizer implements ProcessorNormalizer {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");

    @Override
    public String processorName() {
        return "ProcessorBeta";
    }

    @Override
    public NormalizedChargebackRecord normalize(RawChargebackRecord raw) {
        Map<String, String> f = raw.getFields();
        log.debug("Normalizing ProcessorBeta record: {}", f.get("cbId"));

        String chargebackId  = require(f, "cbId");
        String transactionId = require(f, "txnReference");
        String amountCents   = require(f, "amountCents");
        String currency      = require(f, "currencyCode");
        String reasonCode    = require(f, "reasonCode");
        String dateStr       = require(f, "chargebackDate");
        String cardBin       = require(f, "binNumber");
        String issuerCountry = require(f, "issuingCountry");

        return NormalizedChargebackRecord.builder()
                .chargebackId(chargebackId)
                .transactionId(transactionId)
                .processorName(processorName())
                .amount(parseCents(amountCents))
                .currency(currency.toUpperCase())
                .reasonCode(reasonCode)
                .chargebackDate(LocalDate.parse(dateStr, DATE_FORMAT))
                .cardBin(sanitizeBin(cardBin))
                .issuerCountry(issuerCountry.toUpperCase())
                .rawPayload(raw.getRawPayload())
                .build();
    }

    private String require(Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "ProcessorBeta: missing required field '" + key + "'");
        }
        return value.trim();
    }

    private BigDecimal parseCents(String cents) {
        try {
            long centValue = Long.parseLong(cents.trim());
            return BigDecimal.valueOf(centValue).movePointLeft(2);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ProcessorBeta: invalid amountCents: " + cents);
        }
    }

    private String sanitizeBin(String bin) {
        String digits = bin.replaceAll("[^0-9]", "");
        return digits.substring(0, Math.min(8, digits.length()));
    }
}