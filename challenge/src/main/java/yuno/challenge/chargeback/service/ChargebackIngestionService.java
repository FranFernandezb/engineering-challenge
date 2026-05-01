package yuno.challenge.chargeback.service;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import yuno.challenge.chargeback.dto.BatchIngestResult;
import yuno.challenge.chargeback.dto.ChargebackIngestRequest;
import yuno.challenge.chargeback.dto.ChargebackResponse;
import yuno.challenge.chargeback.model.Chargeback;
import yuno.challenge.chargeback.model.IngestSource;
import yuno.challenge.chargeback.model.ReasonCategory;
import yuno.challenge.chargeback.normalization.*;
import yuno.challenge.chargeback.repository.ChargebackRepository;
import yuno.challenge.common.exception.ChargebackNotFoundException;
import yuno.challenge.common.exception.DuplicateChargebackException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChargebackIngestionService {

    private final ChargebackRepository chargebackRepository;
    private final ProcessorNormalizerRegistry normalizerRegistry;
    private final EnrichmentService enrichmentService;
    private final ObjectMapper objectMapper;

    // -----------------------------------------------------------------------
    // Single record ingestion (API)
    // -----------------------------------------------------------------------

    @Transactional
    public ChargebackResponse ingest(ChargebackIngestRequest request, IngestSource source) {
        String rawPayload = serialize(request.getFields());

        RawChargebackRecord raw = RawChargebackRecord.builder()
                .processorName(request.getProcessorName())
                .fields(request.getFields())
                .rawPayload(rawPayload)
                .build();

        ProcessorNormalizer normalizer = normalizerRegistry.resolve(request.getProcessorName());
        NormalizedChargebackRecord normalized = normalizer.normalize(raw);

        String idempotencyKey = buildIdempotencyKey(normalized.getProcessorName(), normalized.getChargebackId());
        checkDuplicate(idempotencyKey);

        Chargeback chargeback = toEntity(normalized, idempotencyKey, source);
        chargebackRepository.save(chargeback);

        enrichmentService.enrich(chargeback);
        chargebackRepository.save(chargeback);

        log.info("Ingested chargeback: id={}, processor={}, reason={}, category={}",
                chargeback.getChargebackId(), chargeback.getProcessorName(),
                chargeback.getReasonCode(), chargeback.getReasonCategory());

        return ChargebackResponse.from(chargeback);
    }

    // -----------------------------------------------------------------------
    // Batch ingestion (JSON array)
    // -----------------------------------------------------------------------

    @Transactional
    public BatchIngestResult ingestBatch(List<ChargebackIngestRequest> requests) {
        List<String> successIds = new ArrayList<>();
        List<BatchIngestResult.IngestError> errors = new ArrayList<>();
        int duplicates = 0;

        for (int i = 0; i < requests.size(); i++) {
            ChargebackIngestRequest req = requests.get(i);
            try {
                ChargebackResponse response = ingest(req, IngestSource.BATCH);
                successIds.add(response.getChargebackId());
            } catch (DuplicateChargebackException ex) {
                duplicates++;
                log.debug("Batch row {}: duplicate skipped", i);
            } catch (Exception ex) {
                log.warn("Batch row {} failed: {}", i, ex.getMessage());
                errors.add(BatchIngestResult.IngestError.builder()
                        .rowIndex(i)
                        .rawId(req.getFields().getOrDefault("dispute_reference_number",
                                req.getFields().getOrDefault("cbId",
                                        req.getFields().getOrDefault("DISPUTE_ID", "unknown"))))
                        .errorMessage(ex.getMessage())
                        .errorCode(ex.getClass().getSimpleName())
                        .build());
            }
        }

        return BatchIngestResult.builder()
                .total(requests.size())
                .succeeded(successIds.size())
                .failed(errors.size())
                .duplicates(duplicates)
                .successfulIds(successIds)
                .errors(errors)
                .build();
    }

    // -----------------------------------------------------------------------
    // CSV file upload ingestion
    // -----------------------------------------------------------------------

    @Transactional
    public BatchIngestResult ingestCsv(MultipartFile file, String processorName) {
        List<ChargebackIngestRequest> requests = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord record : parser) {
                Map<String, String> fields = new HashMap<>();
                record.toMap().forEach(fields::put);

                ChargebackIngestRequest req = new ChargebackIngestRequest();
                req.setProcessorName(processorName);
                req.setFields(fields);
                requests.add(req);
            }

        } catch (Exception ex) {
            log.error("CSV parsing failed for processor {}: {}", processorName, ex.getMessage(), ex);
            throw new IllegalArgumentException("Failed to parse CSV file: " + ex.getMessage());
        }

        log.info("Parsed {} records from CSV for processor {}", requests.size(), processorName);
        return ingestBatch(requests);
    }

    // -----------------------------------------------------------------------
    // Query
    // -----------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ChargebackResponse findById(String chargebackId) {
        return chargebackRepository.findByChargebackId(chargebackId)
                .map(ChargebackResponse::from)
                .orElseThrow(() -> new ChargebackNotFoundException(chargebackId));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void checkDuplicate(String idempotencyKey) {
        if (chargebackRepository.existsByIdempotencyKey(idempotencyKey)) {
            throw new DuplicateChargebackException(idempotencyKey);
        }
    }

    private String buildIdempotencyKey(String processorName, String chargebackId) {
        return processorName.toLowerCase() + "::" + chargebackId;
    }

    private Chargeback toEntity(NormalizedChargebackRecord n, String idempotencyKey, IngestSource source) {
        return Chargeback.builder()
                .chargebackId(n.getChargebackId())
                .transactionId(n.getTransactionId())
                .processorName(n.getProcessorName())
                .amount(n.getAmount())
                .currency(n.getCurrency())
                .reasonCode(n.getReasonCode())
                .reasonCategory(ReasonCategory.fromCode(n.getReasonCode()))
                .chargebackDate(n.getChargebackDate())
                .cardBin(n.getCardBin())
                .issuerCountry(n.getIssuerCountry())
                .rawPayload(n.getRawPayload())
                .ingestSource(source)
                .idempotencyKey(idempotencyKey)
                .build();
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return obj.toString();
        }
    }
}
