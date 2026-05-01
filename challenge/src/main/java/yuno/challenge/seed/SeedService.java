package yuno.challenge.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yuno.challenge.chargeback.dto.BatchIngestResult;
import yuno.challenge.chargeback.dto.ChargebackIngestRequest;
import yuno.challenge.chargeback.service.ChargebackIngestionService;
import yuno.challenge.transaction.dto.TransactionIngestRequest;
import yuno.challenge.transaction.service.TransactionIngestionService;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads the bundled demo datasets (transactions + per-processor chargeback CSVs)
 * into the database. Resolves locations relative to the working directory so it
 * works whether you run from the project root or via {@code mvn spring-boot:run}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeedService {

    private final ResourceLoader resourceLoader;
    private final TransactionIngestionService transactionService;
    private final ChargebackIngestionService chargebackService;

    @Value("${seed.transactions:file:./data/transactions.csv}")
    private String transactionsLocation;

    @Value("${seed.chargebacks.alpha:file:./data/chargebacks_processor_alpha.csv}")
    private String alphaLocation;

    @Value("${seed.chargebacks.beta:file:./data/chargebacks_processor_beta.csv}")
    private String betaLocation;

    @Value("${seed.chargebacks.gamma:file:./data/chargebacks_processor_gamma.csv}")
    private String gammaLocation;

    @Transactional
    public SeedReport loadAll() {
        SeedReport report = new SeedReport();
        report.transactions = loadTransactions();
        report.alpha        = loadChargebackCsv(alphaLocation, "ProcessorAlpha");
        report.beta         = loadChargebackCsv(betaLocation,  "ProcessorBeta");
        report.gamma        = loadChargebackCsv(gammaLocation, "ProcessorGamma");
        return report;
    }

    public BatchIngestResult loadTransactions() {
        List<TransactionIngestRequest> txns = parseTransactionsCsv(transactionsLocation);
        return transactionService.ingestBatch(txns);
    }

    public BatchIngestResult loadChargebackCsv(String location, String processorName) {
        List<ChargebackIngestRequest> requests = new ArrayList<>();
        Resource resource = resourceLoader.getResource(location);

        if (!resource.exists()) {
            log.warn("Seed CSV not found at {} — skipping", location);
            return BatchIngestResult.builder()
                    .total(0).succeeded(0).failed(0).duplicates(0)
                    .successfulIds(List.of()).errors(List.of())
                    .build();
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setTrim(true)
                     .build().parse(reader)) {

            for (CSVRecord r : parser) {
                Map<String, String> fields = new HashMap<>();
                r.toMap().forEach(fields::put);
                ChargebackIngestRequest req = new ChargebackIngestRequest();
                req.setProcessorName(processorName);
                req.setFields(fields);
                requests.add(req);
            }
        } catch (Exception ex) {
            log.error("Failed to load chargeback CSV {}: {}", location, ex.getMessage(), ex);
            throw new IllegalStateException("Seed failure on " + location, ex);
        }

        log.info("Loaded {} {} chargeback rows from {}", requests.size(), processorName, location);
        return chargebackService.ingestBatch(requests);
    }

    private List<TransactionIngestRequest> parseTransactionsCsv(String location) {
        List<TransactionIngestRequest> list = new ArrayList<>();
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.warn("Transactions CSV not found at {}", location);
            return list;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
             CSVParser parser = CSVFormat.DEFAULT.builder()
                     .setHeader().setSkipHeaderRecord(true).setTrim(true)
                     .build().parse(reader)) {

            for (CSVRecord r : parser) {
                TransactionIngestRequest req = new TransactionIngestRequest();
                req.setTransactionId(r.get("transaction_id"));
                req.setMerchantId(r.get("merchant_id"));
                req.setMerchantName(r.get("merchant_name"));
                req.setMcc(r.get("mcc"));
                req.setAmount(new BigDecimal(r.get("amount")));
                req.setCurrency(r.get("currency"));
                req.setTransactionDate(LocalDate.parse(r.get("transaction_date")));
                req.setCardBin(r.get("card_bin"));
                if (r.isMapped("card_last_four")) req.setCardLastFour(safeGet(r, "card_last_four"));
                if (r.isMapped("card_type"))      req.setCardType(safeGet(r, "card_type"));
                req.setIssuerCountry(r.get("issuer_country"));
                req.setProcessorName(r.get("processor_name"));
                req.setStatus(r.get("status"));
                list.add(req);
            }
        } catch (Exception ex) {
            log.error("Failed to load transactions CSV {}: {}", location, ex.getMessage(), ex);
            throw new IllegalStateException("Seed failure on transactions CSV", ex);
        }
        log.info("Loaded {} transactions from {}", list.size(), location);
        return list;
    }

    private static String safeGet(CSVRecord r, String key) {
        try {
            String v = r.get(key);
            return (v == null || v.isBlank()) ? null : v;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // --- Result wrapper ---
    public static class SeedReport {
        public BatchIngestResult transactions;
        public BatchIngestResult alpha;
        public BatchIngestResult beta;
        public BatchIngestResult gamma;
    }
}
