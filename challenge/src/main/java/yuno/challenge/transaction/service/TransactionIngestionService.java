package yuno.challenge.transaction.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import yuno.challenge.chargeback.dto.BatchIngestResult;
import yuno.challenge.transaction.dto.TransactionIngestRequest;
import yuno.challenge.transaction.dto.TransactionResponse;
import yuno.challenge.transaction.model.Transaction;
import yuno.challenge.transaction.repository.TransactionRepository;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles ingestion of original transactions. Used both for API loading and
 * for the seed-from-CSV pipeline.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionIngestionService {

    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionResponse ingest(TransactionIngestRequest req) {
        return transactionRepository.findByTransactionId(req.getTransactionId())
                .map(existing -> {
                    log.debug("Transaction {} already exists — skipping", req.getTransactionId());
                    return TransactionResponse.from(existing);
                })
                .orElseGet(() -> {
                    Transaction txn = toEntity(req);
                    Transaction saved = transactionRepository.save(txn);
                    log.debug("Ingested transaction {}", saved.getTransactionId());
                    return TransactionResponse.from(saved);
                });
    }

    @Transactional
    public BatchIngestResult ingestBatch(List<TransactionIngestRequest> requests) {
        List<String> successIds = new ArrayList<>();
        List<BatchIngestResult.IngestError> errors = new ArrayList<>();
        int duplicates = 0;

        for (int i = 0; i < requests.size(); i++) {
            TransactionIngestRequest req = requests.get(i);
            try {
                if (transactionRepository.findByTransactionId(req.getTransactionId()).isPresent()) {
                    duplicates++;
                    continue;
                }
                transactionRepository.save(toEntity(req));
                successIds.add(req.getTransactionId());
            } catch (Exception ex) {
                log.warn("Transaction batch row {} failed: {}", i, ex.getMessage());
                errors.add(BatchIngestResult.IngestError.builder()
                        .rowIndex(i)
                        .rawId(req.getTransactionId())
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

    @Transactional
    public BatchIngestResult ingestCsv(MultipartFile file) {
        return ingestBatch(parseCsv(file));
    }

    public List<TransactionIngestRequest> parseCsv(MultipartFile file) {
        List<TransactionIngestRequest> requests = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            for (CSVRecord r : parser) {
                requests.add(toRequest(r));
            }
        } catch (Exception ex) {
            log.error("Transaction CSV parsing failed: {}", ex.getMessage(), ex);
            throw new IllegalArgumentException("Failed to parse transactions CSV: " + ex.getMessage());
        }
        log.info("Parsed {} transaction rows from CSV", requests.size());
        return requests;
    }

    private TransactionIngestRequest toRequest(CSVRecord r) {
        TransactionIngestRequest req = new TransactionIngestRequest();
        req.setTransactionId(r.get("transaction_id"));
        req.setMerchantId(r.get("merchant_id"));
        req.setMerchantName(r.get("merchant_name"));
        req.setMcc(r.get("mcc"));
        req.setAmount(new BigDecimal(r.get("amount")));
        req.setCurrency(r.get("currency"));
        req.setTransactionDate(LocalDate.parse(r.get("transaction_date")));
        req.setCardBin(r.get("card_bin"));
        if (r.isMapped("card_last_four")) req.setCardLastFour(r.get("card_last_four"));
        if (r.isMapped("card_type"))      req.setCardType(r.get("card_type"));
        req.setIssuerCountry(r.get("issuer_country"));
        req.setProcessorName(r.get("processor_name"));
        req.setStatus(r.get("status"));
        return req;
    }

    private Transaction toEntity(TransactionIngestRequest req) {
        return Transaction.builder()
                .transactionId(req.getTransactionId())
                .merchantId(req.getMerchantId())
                .merchantName(req.getMerchantName())
                .mcc(req.getMcc())
                .amount(req.getAmount())
                .currency(req.getCurrency().toUpperCase())
                .transactionDate(req.getTransactionDate())
                .cardBin(req.getCardBin().replaceAll("[^0-9]", ""))
                .cardLastFour(req.getCardLastFour())
                .cardType(req.getCardType())
                .issuerCountry(req.getIssuerCountry().toUpperCase())
                .processorName(req.getProcessorName())
                .status(req.getStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public long count() {
        return transactionRepository.count();
    }
}
