package yuno.challenge.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import yuno.challenge.transaction.service.TransactionIngestionService;

/**
 * Seeds the demo dataset on startup if the database is empty.
 * Set {@code seed.auto-load=false} (or env {@code SEED_AUTO_LOAD=false}) to disable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupSeeder {

    private final SeedService seedService;
    private final TransactionIngestionService transactionService;

    @Value("${seed.auto-load:true}")
    private boolean autoLoad;

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        if (!autoLoad) {
            log.info("Auto-seed disabled (seed.auto-load=false). Use POST /api/seed/load to load manually.");
            return;
        }
        if (transactionService.count() > 0) {
            log.info("Database already populated ({} txns) — skipping auto-seed.", transactionService.count());
            return;
        }
        try {
            log.info("Auto-seeding demo dataset on startup...");
            SeedService.SeedReport report = seedService.loadAll();
            log.info("Auto-seed done. Transactions: {} succeeded / {} failed. " +
                            "Chargebacks — Alpha: {}, Beta: {}, Gamma: {}",
                    report.transactions.getSucceeded(),
                    report.transactions.getFailed(),
                    report.alpha != null ? report.alpha.getSucceeded() : 0,
                    report.beta  != null ? report.beta.getSucceeded()  : 0,
                    report.gamma != null ? report.gamma.getSucceeded() : 0);
        } catch (Exception ex) {
            log.warn("Auto-seed skipped due to error (datasets may be missing): {}", ex.getMessage());
        }
    }
}
