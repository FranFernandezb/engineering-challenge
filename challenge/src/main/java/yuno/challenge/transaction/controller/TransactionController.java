package yuno.challenge.transaction.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import yuno.challenge.chargeback.dto.BatchIngestResult;
import yuno.challenge.common.response.ApiResponse;
import yuno.challenge.transaction.dto.TransactionIngestRequest;
import yuno.challenge.transaction.dto.TransactionResponse;
import yuno.challenge.transaction.service.TransactionIngestionService;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionIngestionService service;

    /**
     * POST /api/transactions  — ingest a single transaction.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<TransactionResponse>> ingest(
            @Valid @RequestBody TransactionIngestRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Transaction ingested", service.ingest(req)));
    }

    /**
     * POST /api/transactions/batch  — ingest a JSON array of transactions.
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<BatchIngestResult>> ingestBatch(
            @RequestBody List<@Valid TransactionIngestRequest> requests) {
        BatchIngestResult result = service.ingestBatch(requests);
        HttpStatus status = result.getFailed() == 0 ? HttpStatus.CREATED : HttpStatus.MULTI_STATUS;
        return ResponseEntity.status(status)
                .body(ApiResponse.ok("Batch ingestion complete", result));
    }

    /**
     * POST /api/transactions/upload  — upload a CSV with the unified transaction schema.
     * Required headers: transaction_id, merchant_id, merchant_name, mcc, amount, currency,
     * transaction_date, card_bin, issuer_country, processor_name, status.
     * Optional: card_last_four, card_type.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BatchIngestResult>> uploadCsv(
            @RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Uploaded file is empty", "EMPTY_FILE"));
        }
        BatchIngestResult result = service.ingestCsv(file);
        HttpStatus status = result.getFailed() == 0 ? HttpStatus.CREATED : HttpStatus.MULTI_STATUS;
        return ResponseEntity.status(status)
                .body(ApiResponse.ok("CSV ingestion complete", result));
    }

    /**
     * GET /api/transactions/count  — quick health check.
     */
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> count() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of("transactions", service.count())));
    }
}
