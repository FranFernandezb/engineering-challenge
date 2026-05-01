package yuno.challenge.chargeback.normalization;


/**
 * Strategy interface for normalizing processor-specific chargeback records
 * into the unified {@link NormalizedChargebackRecord} schema.
 * <p>
 * Each supported processor has its own implementation registered as a Spring bean.
 * The {@link ProcessorNormalizerRegistry} resolves the correct strategy at runtime.
 */
public interface ProcessorNormalizer {

    /**
     * The canonical processor name this normalizer handles.
     * Must match the {@code processor_name} field in incoming payloads (case-insensitive).
     */
    String processorName();

    /**
     * Normalize a raw record from this processor into the unified schema.
     *
     * @param raw the raw record with all original fields
     * @return a normalized, validated record ready for persistence
     * @throws IllegalArgumentException if required fields are missing or unparseable
     */
    NormalizedChargebackRecord normalize(RawChargebackRecord raw);
}
