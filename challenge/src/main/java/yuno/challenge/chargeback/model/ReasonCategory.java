package yuno.challenge.chargeback.model;

/**
 * Normalized dispute categories derived from processor-specific reason codes.
 * <p>
 * Visa codes: 10.x = fraud, 11.x = authorization, 12.x = processing errors, 13.x = consumer disputes
 * Mastercard codes: 4xxx series
 * ProcessorBeta/Gamma may use their own internal code schemes
 */
public enum ReasonCategory {

    /** Unauthorized transaction — CNP fraud, card-not-present, lost/stolen */
    FRAUD,

    /** Goods or services not received, order cancellation, services not as described */
    FULFILLMENT,

    /** Incorrect amounts, duplicate processing, expired card charges */
    PROCESSING_ERROR,

    /** Authorization-related: no authorization, wrong amount authorized */
    AUTHORIZATION,

    /** Subscription / recurring billing disputes */
    RECURRING_BILLING,

    /** Catch-all for unmapped or new reason codes */
    OTHER;

    /**
     * Maps a raw reason code (from any processor) to the normalized category.
     * Reason code formats:
     *   - Visa:       10.4, 13.1, 11.3 etc.
     *   - Mastercard: 4853, 4837, 4831 etc.
     *   - Amex:       C08, FR2 etc.
     *   - Internal:   ProcessorBeta/Gamma custom codes
     */
    public static ReasonCategory fromCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) return OTHER;

        String code = rawCode.trim().toUpperCase();

        // --- Visa fraud codes (10.x) ---
        if (code.startsWith("10.")) return FRAUD;

        // --- Visa authorization (11.x) ---
        if (code.startsWith("11.")) return AUTHORIZATION;

        // --- Visa processing errors (12.x) ---
        if (code.startsWith("12.")) return PROCESSING_ERROR;

        // --- Visa consumer disputes (13.x) ---
        if (code.equals("13.1") || code.equals("13.2") || code.equals("13.5")) return FULFILLMENT;
        if (code.equals("13.3") || code.equals("13.7")) return FULFILLMENT;
        if (code.equals("13.6")) return RECURRING_BILLING;
        if (code.startsWith("13.")) return FULFILLMENT;

        // --- Mastercard fraud ---
        if (code.equals("4837") || code.equals("4870") || code.equals("4871")) return FRAUD;

        // --- Mastercard fulfillment ---
        if (code.equals("4853") || code.equals("4855") || code.equals("4859")) return FULFILLMENT;

        // --- Mastercard processing ---
        if (code.equals("4831") || code.equals("4834") || code.equals("4842")) return PROCESSING_ERROR;

        // --- Mastercard authorization ---
        if (code.equals("4808") || code.equals("4812") || code.equals("4807")) return AUTHORIZATION;

        // --- Mastercard recurring ---
        if (code.equals("4841")) return RECURRING_BILLING;

        // --- Amex codes ---
        if (code.equals("FR2") || code.equals("FR4") || code.equals("FR6")) return FRAUD;
        if (code.equals("C08") || code.equals("C31") || code.equals("C32")) return FULFILLMENT;
        if (code.equals("A02") || code.equals("A08")) return AUTHORIZATION;

        // --- ProcessorBeta internal codes ---
        if (code.startsWith("PB-FRAUD"))    return FRAUD;
        if (code.startsWith("PB-NORECV"))   return FULFILLMENT;
        if (code.startsWith("PB-DUPCHG"))   return PROCESSING_ERROR;
        if (code.startsWith("PB-AUTH"))     return AUTHORIZATION;

        // --- ProcessorGamma internal codes ---
        if (code.startsWith("PG-UNAUTH"))   return FRAUD;
        if (code.startsWith("PG-NOGOODS")) return FULFILLMENT;
        if (code.startsWith("PG-RECURR"))  return RECURRING_BILLING;

        return OTHER;
    }
}
