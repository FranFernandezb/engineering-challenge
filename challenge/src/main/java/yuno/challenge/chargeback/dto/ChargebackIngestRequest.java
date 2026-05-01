package yuno.challenge.chargeback.dto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * Flexible ingest request that accepts processor-specific raw fields.
 * The {@code processorName} determines which normalizer is used to
 * interpret the {@code fields} map.
 */
@Data
public class ChargebackIngestRequest {

    @NotBlank(message = "processorName is required")
    private String processorName;

    /**
     * Raw fields as received from the processor.
     * Each processor normalizer knows which keys to expect.
     */
    @NotNull(message = "fields map is required and must not be null")
    private Map<String, String> fields;
}
