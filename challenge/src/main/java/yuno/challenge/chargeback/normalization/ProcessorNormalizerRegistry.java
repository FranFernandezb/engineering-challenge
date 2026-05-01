package yuno.challenge.chargeback.normalization;


import yuno.challenge.common.exception.InvalidProcessorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry that discovers all {@link ProcessorNormalizer} beans and routes
 * normalization requests to the correct implementation.
 * <p>
 * New processors are added simply by implementing {@link ProcessorNormalizer}
 * and annotating the class with {@code @Component} — no changes needed here.
 */
@Slf4j
@Component
public class ProcessorNormalizerRegistry {

    private final Map<String, ProcessorNormalizer> normalizersByProcessor;

    public ProcessorNormalizerRegistry(List<ProcessorNormalizer> normalizers) {
        this.normalizersByProcessor = normalizers.stream()
                .collect(Collectors.toMap(
                        n -> n.processorName().toLowerCase(),
                        Function.identity()
                ));
        log.info("Registered {} processor normalizers: {}", normalizersByProcessor.size(),
                normalizersByProcessor.keySet());
    }

    /**
     * Returns the normalizer for the given processor name.
     *
     * @param processorName the processor name from the incoming payload
     * @throws InvalidProcessorException if no normalizer is registered for this processor
     */
    public ProcessorNormalizer resolve(String processorName) {
        ProcessorNormalizer normalizer = normalizersByProcessor.get(processorName.toLowerCase());
        if (normalizer == null) {
            throw new InvalidProcessorException(processorName);
        }
        return normalizer;
    }

    public java.util.Set<String> supportedProcessors() {
        return normalizersByProcessor.keySet();
    }
}
