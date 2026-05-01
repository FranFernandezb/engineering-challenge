package yuno.challenge.chargeback.normalization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Normalizer for ProcessorAlpha.
 * <p>
 * Field mapping (ProcessorAlpha → unified):
 * <pre>
 *   dispute_reference_number → chargebackId
 *   original_txn_id          → transactionId
 *   dispute_amount           → amount
 *   dispute_currency         → currency
 *   visa_reason_code         → reasonCode  (e.g. "10.4", "13.1")
 *   dispute_date             → chargebackDate (ISO 8601: yyyy-MM-dd)
 *   card_bin                 → cardBin
 *   card_issuer_country      → issuerCountry
 * </pre>
 */
@Slf4j
@Component
public class ProcessorAlphaNormalizer implements ProcessorNormalizer {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Override
    public String processorName() {
        return "ProcessorAlpha";
    }

    @Override
    public NormalizedChargebackRecord normalize(RawChargebackRecord raw) {
        Map<String, String> f = raw.getFields();
        log.debug("Normalizing ProcessorAlpha record: {}", f.get("dispute_reference_number"));

        String chargebackId = require(f, "dispute_reference_number");
        String transactionId = require(f, "original_txn_id");
        String amountStr     = require(f, "dispute_amount");
        String currency      = require(f, "dispute_currency");
        String reasonCode    = require(f, "visa_reason_code");
        String dateStr       = require(f, "dispute_date");
        String cardBin       = require(f, "card_bin");
        String issuerCountry = require(f, "card_issuer_country");

        return NormalizedChargebackRecord.builder()
                .chargebackId(chargebackId)
                .transactionId(transactionId)
                .processorName(processorName())
                .amount(parseMoney(amountStr))
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
                    "ProcessorAlpha: missing required field '" + key + "'");
        }
        return value.trim();
    }

    private BigDecimal parseMoney(String value) {
        try {
            return new BigDecimal(value.replace(",", "").trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("ProcessorAlpha: invalid amount value: " + value);
        }
    }

    private String sanitizeBin(String bin) {
        // Keep only the first 8 digits
        return bin.replaceAll("[^0-9]", "").substring(0, Math.min(8, bin.replaceAll("[^0-9]", "").length()));
    }
}
