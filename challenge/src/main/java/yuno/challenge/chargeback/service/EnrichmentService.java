package yuno.challenge.chargeback.service;


import yuno.challenge.chargeback.model.Chargeback;
import yuno.challenge.transaction.model.Transaction;
import yuno.challenge.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Enriches a persisted {@link Chargeback} record with metadata from the
 * original {@link Transaction}.
 * <p>
 * Enrichment is best-effort: if the transaction is not found (e.g. the
 * original purchase was processed before this service was deployed), the
 * chargeback is still saved but without merchant/MCC context.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnrichmentService {

    private final TransactionRepository transactionRepository;

    /**
     * Attempts to enrich the chargeback by looking up the original transaction.
     * Mutates the chargeback object in place.
     *
     * @param chargeback the chargeback to enrich (already persisted)
     * @return true if enrichment succeeded, false if the transaction was not found
     */
    public boolean enrich(Chargeback chargeback) {
        Optional<Transaction> txnOpt = transactionRepository.findByTransactionId(
                chargeback.getTransactionId());

        if (txnOpt.isEmpty()) {
            log.warn("Enrichment: no transaction found for transaction_id={}, chargeback_id={}",
                    chargeback.getTransactionId(), chargeback.getChargebackId());
            return false;
        }

        Transaction txn = txnOpt.get();
        chargeback.setMerchantId(txn.getMerchantId());
        chargeback.setMerchantName(txn.getMerchantName());
        chargeback.setMcc(txn.getMcc());
        chargeback.setOriginalTransactionDate(txn.getTransactionDate());
        chargeback.setOriginalAmount(txn.getAmount());

        log.debug("Enriched chargeback {} with transaction data: merchant={}, mcc={}",
                chargeback.getChargebackId(), txn.getMerchantName(), txn.getMcc());
        return true;
    }
}
