package yuno.challenge.chargeback.normalization;


import lombok.Builder;
import lombok.Getter;

import java.util.Map;

/**
 * Raw, un-normalized chargeback record as received from a processor.
 * Intentionally uses String for all fields so each processor adapter can
 * do its own type-specific parsing before handing off to the normalizer.
 */
@Getter
@Builder
public class RawChargebackRecord {

    private final String processorName;

    /** All raw fields keyed exactly as the processor sent them */
    private final Map<String, String> fields;

    /** The original serialized payload (JSON body or CSV row) for audit */
    private final String rawPayload;
}