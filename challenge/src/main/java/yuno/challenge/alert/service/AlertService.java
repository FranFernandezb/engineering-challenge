package yuno.challenge.alert.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import yuno.challenge.alert.dto.Alert;
import yuno.challenge.alert.dto.AlertReport;
import yuno.challenge.analytics.dto.bin.BinClusteringResponse;
import yuno.challenge.analytics.dto.processor.ProcessorRankingResponse;
import yuno.challenge.analytics.dto.reasoncode.ReasonCodeBreakdownResponse;
import yuno.challenge.analytics.service.AnalyticsService;
import yuno.challenge.chargeback.repository.ChargebackRepository;
import yuno.challenge.transaction.repository.TransactionRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pattern-detection / alerting engine.
 * <p>
 * Runs a set of independent rules over the ingested data and aggregates the
 * findings into an {@link AlertReport}. Adding a new rule = a new private
 * method appended to {@link #runAllRules}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlertService {

    // --- Network thresholds ---
    private static final BigDecimal OVERALL_WARNING_PCT  = new BigDecimal("1.0");  // amber
    private static final BigDecimal OVERALL_CRITICAL_PCT = new BigDecimal("1.5");  // red — Visa/MC threshold

    // --- Processor hotspot ---
    private static final BigDecimal PROCESSOR_HOTSPOT_MULTIPLIER = new BigDecimal("2.0");

    // --- BIN cluster ---
    private static final long BIN_CLUSTER_MIN_COUNT = 8L;

    // --- Reason code spike ---
    private static final BigDecimal REASON_SPIKE_SHARE_PCT = new BigDecimal("60.0");

    // --- Geographic concentration ---
    private static final BigDecimal GEO_CONCENTRATION_CB_SHARE_PCT  = new BigDecimal("40.0");
    private static final BigDecimal GEO_CONCENTRATION_TXN_SHARE_PCT = new BigDecimal("20.0");

    private final AnalyticsService analyticsService;
    private final ChargebackRepository chargebackRepository;
    private final TransactionRepository transactionRepository;

    /**
     * Executes every detection rule against the data in [from..to] and returns
     * a consolidated report. Order of rules in the response is deterministic
     * (CRITICAL first, then WARNING, then INFO).
     */
    public AlertReport runAllRules(LocalDate from, LocalDate to) {
        log.info("Running pattern-detection rules for window {} → {}", from, to);

        long totalCb  = chargebackRepository.countInRange(from, to);
        long totalTxn = transactionRepository.countInRange(from, to);
        BigDecimal overallRate = rate(totalCb, totalTxn);

        List<Alert> alerts = new ArrayList<>();

        // --- Rule 1: overall threshold breach -------------------------------
        detectOverallThreshold(overallRate).ifPresent(alerts::add);

        // --- Rule 2: processor hotspot --------------------------------------
        alerts.addAll(detectProcessorHotspot(from, to, overallRate));

        // --- Rule 3: BIN clustering -----------------------------------------
        alerts.addAll(detectBinClustering(from, to));

        // --- Rule 4: reason-code spike --------------------------------------
        alerts.addAll(detectReasonCodeSpike(from, to));

        // --- Rule 5: geographic concentration -------------------------------
        alerts.addAll(detectGeoConcentration(from, to, totalTxn));

        // Sort: CRITICAL → WARNING → INFO
        alerts.sort((a, b) -> b.getSeverity().ordinal() - a.getSeverity().ordinal());

        int crit = (int) alerts.stream().filter(a -> a.getSeverity() == Alert.Severity.CRITICAL).count();
        int warn = (int) alerts.stream().filter(a -> a.getSeverity() == Alert.Severity.WARNING).count();
        int info = (int) alerts.stream().filter(a -> a.getSeverity() == Alert.Severity.INFO).count();

        String overallRisk = AnalyticsService.classifyRisk(overallRate);

        log.info("Alert run complete: {} alerts ({} crit / {} warn / {} info)",
                alerts.size(), crit, warn, info);

        return AlertReport.builder()
                .from(from).to(to)
                .totalChargebacks(totalCb)
                .totalTransactions(totalTxn)
                .overallRatePercent(overallRate)
                .totalAlerts(alerts.size())
                .criticalCount(crit)
                .warningCount(warn)
                .infoCount(info)
                .overallRisk(overallRisk)
                .alerts(alerts)
                .build();
    }

    // -----------------------------------------------------------------------
    // Rule 1: Overall threshold breach
    // -----------------------------------------------------------------------
    private java.util.Optional<Alert> detectOverallThreshold(BigDecimal rate) {
        if (rate.compareTo(OVERALL_CRITICAL_PCT) >= 0) {
            return java.util.Optional.of(Alert.builder()
                    .ruleId("OVERALL_RATE_CRITICAL")
                    .dimension("OVERALL")
                    .severity(Alert.Severity.CRITICAL)
                    .message(String.format(
                            "CRITICAL: Overall chargeback rate is %s%%, exceeding the 1.5%% Visa/Mastercard threshold. " +
                                    "Card-network termination risk is imminent if this persists for two consecutive months.",
                            rate.toPlainString()))
                    .metricValue(rate)
                    .threshold(OVERALL_CRITICAL_PCT)
                    .build());
        }
        if (rate.compareTo(OVERALL_WARNING_PCT) >= 0) {
            return java.util.Optional.of(Alert.builder()
                    .ruleId("OVERALL_RATE_WARNING")
                    .dimension("OVERALL")
                    .severity(Alert.Severity.WARNING)
                    .message(String.format(
                            "WARNING: Overall chargeback rate is %s%%, above the 1.0%% early-warning threshold and " +
                                    "approaching the 1.5%% network limit.",
                            rate.toPlainString()))
                    .metricValue(rate)
                    .threshold(OVERALL_WARNING_PCT)
                    .build());
        }
        return java.util.Optional.empty();
    }

    // -----------------------------------------------------------------------
    // Rule 2: Processor hotspot (>2x overall average)
    // -----------------------------------------------------------------------
    private List<Alert> detectProcessorHotspot(LocalDate from, LocalDate to, BigDecimal overallRate) {
        if (overallRate.signum() == 0) return List.of();

        ProcessorRankingResponse ranking = analyticsService.processorRanking(from, to);
        BigDecimal multiplier = PROCESSOR_HOTSPOT_MULTIPLIER;
        BigDecimal hotspotThreshold = overallRate.multiply(multiplier).setScale(4, RoundingMode.HALF_UP);

        List<Alert> alerts = new ArrayList<>();
        for (ProcessorRankingResponse.ProcessorStat p : ranking.getProcessors()) {
            if (p.getTransactionCount() == 0) continue;
            if (p.getRatePercent().compareTo(hotspotThreshold) >= 0) {
                BigDecimal multipleOfAvg = overallRate.signum() == 0
                        ? BigDecimal.ZERO
                        : p.getRatePercent().divide(overallRate, 2, RoundingMode.HALF_UP);

                Map<String, Object> detail = new HashMap<>();
                detail.put("processor", p.getProcessorName());
                detail.put("processorRatePercent", p.getRatePercent());
                detail.put("overallRatePercent", overallRate);
                detail.put("multipleOfAverage", multipleOfAvg);
                detail.put("chargebacks", p.getChargebackCount());
                detail.put("transactions", p.getTransactionCount());

                alerts.add(Alert.builder()
                        .ruleId("PROCESSOR_HOTSPOT")
                        .dimension("PROCESSOR")
                        .severity(Alert.Severity.CRITICAL)
                        .message(String.format(
                                "%s chargeback rate is %s%% — %sx the portfolio average of %s%%. " +
                                        "Investigate routing or fraud patterns specific to this processor.",
                                p.getProcessorName(),
                                p.getRatePercent().toPlainString(),
                                multipleOfAvg.toPlainString(),
                                overallRate.toPlainString()))
                        .metricValue(p.getRatePercent())
                        .threshold(hotspotThreshold)
                        .details(detail)
                        .build());
            }
        }
        return alerts;
    }

    // -----------------------------------------------------------------------
    // Rule 3: BIN clustering (>= 8 chargebacks from the same BIN)
    // -----------------------------------------------------------------------
    private List<Alert> detectBinClustering(LocalDate from, LocalDate to) {
        BinClusteringResponse clustering = analyticsService.binClustering(from, to);
        List<Alert> alerts = new ArrayList<>();

        for (BinClusteringResponse.BinStat b : clustering.getBins()) {
            if (b.getChargebackCount() >= BIN_CLUSTER_MIN_COUNT) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("cardBin", b.getCardBin());
                detail.put("issuerCountry", b.getIssuerCountry());
                detail.put("chargebackCount", b.getChargebackCount());
                detail.put("transactionCount", b.getTransactionCount());
                detail.put("ratePercent", b.getRatePercent());

                alerts.add(Alert.builder()
                        .ruleId("BIN_CLUSTER")
                        .dimension("BIN")
                        .severity(Alert.Severity.WARNING)
                        .message(String.format(
                                "BIN %s (issuer country %s) has %d chargebacks — likely compromised card range " +
                                        "or fraud-prone issuer. Consider step-up auth or temporary block.",
                                b.getCardBin(), b.getIssuerCountry(), b.getChargebackCount()))
                        .metricValue(BigDecimal.valueOf(b.getChargebackCount()))
                        .threshold(BigDecimal.valueOf(BIN_CLUSTER_MIN_COUNT))
                        .details(detail)
                        .build());
            }
        }
        return alerts;
    }

    // -----------------------------------------------------------------------
    // Rule 4: Reason-code spike (a single category > 60% of disputes)
    // -----------------------------------------------------------------------
    private List<Alert> detectReasonCodeSpike(LocalDate from, LocalDate to) {
        ReasonCodeBreakdownResponse breakdown = analyticsService.reasonCodeBreakdown(from, to);
        if (breakdown.getTotalChargebacks() == 0) return List.of();

        // Aggregate by category, not by raw code, since codes vary across networks.
        Map<String, Long> byCategory = new HashMap<>();
        Map<String, BigDecimal> amountByCategory = new HashMap<>();
        for (ReasonCodeBreakdownResponse.ReasonStat s : breakdown.getReasonCodes()) {
            byCategory.merge(s.getReasonCategory(), s.getChargebackCount(), Long::sum);
            amountByCategory.merge(s.getReasonCategory(), s.getTotalAmount(), BigDecimal::add);
        }

        List<Alert> alerts = new ArrayList<>();
        long total = breakdown.getTotalChargebacks();

        for (Map.Entry<String, Long> e : byCategory.entrySet()) {
            BigDecimal share = BigDecimal.valueOf(e.getValue() * 100.0 / total)
                    .setScale(2, RoundingMode.HALF_UP);
            if (share.compareTo(REASON_SPIKE_SHARE_PCT) >= 0) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("category", e.getKey());
                detail.put("count", e.getValue());
                detail.put("totalAmount", amountByCategory.getOrDefault(e.getKey(), BigDecimal.ZERO));
                detail.put("sharePercent", share);

                String guidance = switch (e.getKey()) {
                    case "FRAUD" -> "Investigate fraud vectors: BIN ranges, geographies, attack patterns. " +
                            "Consider strengthening 3DS or velocity rules.";
                    case "FULFILLMENT" -> "Surface to merchant ops: missed deliveries or product-as-described disputes.";
                    case "PROCESSING_ERROR" -> "Check for duplicate captures, incorrect amounts, or settlement bugs.";
                    case "AUTHORIZATION" -> "Review auth/capture timing; possible expired-auth charges.";
                    default -> "Review the underlying disputes to identify the root cause.";
                };

                alerts.add(Alert.builder()
                        .ruleId("REASON_CODE_SPIKE")
                        .dimension("REASON")
                        .severity(Alert.Severity.WARNING)
                        .message(String.format(
                                "%s disputes account for %s%% of all chargebacks (%d of %d). %s",
                                e.getKey(), share.toPlainString(), e.getValue(), total, guidance))
                        .metricValue(share)
                        .threshold(REASON_SPIKE_SHARE_PCT)
                        .details(detail)
                        .build());
            }
        }
        return alerts;
    }

    // -----------------------------------------------------------------------
    // Rule 5: Geographic concentration (>40% chargebacks from country with <20% transactions)
    // -----------------------------------------------------------------------
    private List<Alert> detectGeoConcentration(LocalDate from, LocalDate to, long totalTxn) {
        if (totalTxn == 0) return List.of();

        BinClusteringResponse clustering = analyticsService.binClustering(from, to);
        List<Alert> alerts = new ArrayList<>();

        for (BinClusteringResponse.CountryStat c : clustering.getCountries()) {
            BigDecimal txnShare = c.getTransactionCount() > 0
                    ? BigDecimal.valueOf(c.getTransactionCount() * 100.0 / totalTxn)
                            .setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            if (c.getSharePercent().compareTo(GEO_CONCENTRATION_CB_SHARE_PCT) >= 0
                    && txnShare.compareTo(GEO_CONCENTRATION_TXN_SHARE_PCT) <= 0) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("country", c.getIssuerCountry());
                detail.put("chargebackSharePercent", c.getSharePercent());
                detail.put("transactionSharePercent", txnShare);
                detail.put("chargebackCount", c.getChargebackCount());
                detail.put("transactionCount", c.getTransactionCount());

                alerts.add(Alert.builder()
                        .ruleId("GEO_CONCENTRATION")
                        .dimension("COUNTRY")
                        .severity(Alert.Severity.CRITICAL)
                        .message(String.format(
                                "Country %s accounts for %s%% of chargebacks but only %s%% of transactions — " +
                                        "highly disproportionate fraud signal. Consider country-level risk rules.",
                                c.getIssuerCountry(), c.getSharePercent().toPlainString(), txnShare.toPlainString()))
                        .metricValue(c.getSharePercent())
                        .threshold(GEO_CONCENTRATION_CB_SHARE_PCT)
                        .details(detail)
                        .build());
            }
        }
        return alerts;
    }

    // -----------------------------------------------------------------------
    private static BigDecimal rate(long chargebacks, long transactions) {
        if (transactions <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf((double) chargebacks / transactions * 100)
                .setScale(4, RoundingMode.HALF_UP);
    }
}
