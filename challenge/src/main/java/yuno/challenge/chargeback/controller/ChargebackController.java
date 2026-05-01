package yuno.challenge.chargeback.controller;


import yuno.challenge.chargeback.dto.BatchIngestResult;
import yuno.challenge.chargeback.dto.ChargebackIngestRequest;
import yuno.challenge.chargeback.dto.ChargebackResponse;
import yuno.challenge.chargeback.model.IngestSource;
import yuno.challenge.chargeback.service.ChargebackIngestionService;
import yuno.challenge.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/chargebacks")
@RequiredArgsConstructor
public class ChargebackController {

    private final ChargebackIngestionService ingestionService;

    /**
     * Ingest a single chargeback record from any supported processor.
     * <p>
     * The {@code processorName} field in the request body determines which
     * normalizer is applied to the {@code fields} map.
     *
     * Example (ProcessorAlpha):
     * POST /api/chargebacks/ingest
     * {
     *   "processorName": "ProcessorAlpha",
     *   "fields": {
     *     "dispute_reference_number": "ALF-CB-2025-001",
     *     "original_txn_id": "TXN-PA-001",
     *     "dispute_amount": "299000",
     *     "dispute_currency": "IDR",
     *     "visa_reason_code": "10.4",
     *     "dispute_date": "2025-03-01",
     *     "card_bin": "453210",
     *     "card_issuer_country": "ID"
     *   }
     * }
     */
    @PostMapping("/ingest")
    public ResponseEntity<ApiResponse<ChargebackResponse>> ingest(
            @Valid @RequestBody ChargebackIngestRequest request) {
        log.info("Ingest request received for processor: {}", request.getProcessorName());
        ChargebackResponse response = ingestionService.ingest(request, IngestSource.API);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Chargeback ingested successfully", response));
    }

    /**
     * Ingest a batch of chargeback records (mixed processors allowed).
     * Errors on individual records do not abort the batch.
     */
    @PostMapping("/ingest/batch")
    public ResponseEntity<ApiResponse<BatchIngestResult>> ingestBatch(
            @RequestBody List<@Valid ChargebackIngestRequest> requests) {
        log.info("Batch ingest request: {} records", requests.size());
        BatchIngestResult result = ingestionService.ingestBatch(requests);
        HttpStatus status = result.getFailed() == 0 ? HttpStatus.CREATED : HttpStatus.MULTI_STATUS;
        return ResponseEntity.status(status)
                .body(ApiResponse.ok("Batch ingestion complete", result));
    }

    /**
     * Ingest chargebacks from a CSV file upload.
     * The processorName query param tells the service which normalizer to use
     * (all rows in a CSV file come from the same processor).
     *
     * ProcessorAlpha CSV headers:
     *   dispute_reference_number, original_txn_id, dispute_amount, dispute_currency,
     *   visa_reason_code, dispute_date, card_bin, card_issuer_country
     *
     * ProcessorBeta CSV headers:
     *   cbId, txnReference, amountCents, currencyCode, reasonCode,
     *   chargebackDate, binNumber, issuingCountry
     *
     * ProcessorGamma CSV headers:
     *   DISPUTE_ID, TXN_ID, DISPUTE_AMOUNT, CURRENCY, DISPUTE_CODE,
     *   DISPUTE_EPOCH, BIN, COUNTRY
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BatchIngestResult>> uploadCsv(
            @RequestParam("file") MultipartFile file,
            @RequestParam("processorName") String processorName) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Uploaded file is empty", "EMPTY_FILE"));
        }

        log.info("CSV upload received: filename={}, processor={}, size={} bytes",
                file.getOriginalFilename(), processorName, file.getSize());

        BatchIngestResult result = ingestionService.ingestCsv(file, processorName);
        HttpStatus status = result.getFailed() == 0 ? HttpStatus.CREATED : HttpStatus.MULTI_STATUS;
        return ResponseEntity.status(status)
                .body(ApiResponse.ok("CSV ingestion complete", result));
    }

    /**
     * Retrieve a single chargeback by its chargeback ID.
     */
    @GetMapping("/{chargebackId}")
    public ResponseEntity<ApiResponse<ChargebackResponse>> getById(
            @PathVariable String chargebackId) {
        ChargebackResponse response = ingestionService.findById(chargebackId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
