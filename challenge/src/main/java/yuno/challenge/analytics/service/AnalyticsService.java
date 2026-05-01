package yuno.challenge.analytics.service;


import yuno.challenge.analytics.dto.*;
import yuno.challenge.analytics.dto.bin.BinClusteringResponse;
import yuno.challenge.analytics.dto.chargebackrate.ChargebackRateTrendResponse;
import yuno.challenge.analytics.dto.mccbreakdown.MccBreakdownResponse;
import yuno.challenge.analytics.dto.processor.ProcessorRankingResponse;
import yuno.challenge.analytics.dto.reasoncode.ReasonCodeBreakdownResponse;
import yuno.challenge.chargeback.repository.ChargebackRepository;
import yuno.challenge.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsService {

    // Visa/Mastercard excessive chargeback thresholds
    private static final BigDecimal THRESHOLD_AMBER = new BigDecimal("0.9");  // early warning at 0.9%
    private static final BigDecimal THRESHOLD_RED   = new BigDecimal("1.5");  // network penalty zone

    private final ChargebackRepository chargebackRepository;
    private final TransactionRepository transactionRepository;

    // -----------------------------------------------------------------------
    // Overall chargeback rate for a date range
    // -----------------------------------------------------------------------

    /**
     * Simple rate over a window: chargebacks counted in [from..to] divided by
     * transactions in the same window. Used for at-a-glance KPIs and as the
     * baseline for "processor hotspot" alerts.
     */
    public BigDecimal overallRatePercent(LocalDate from, LocalDate to) {
        long cb  = chargebackRepository.countInRange(from, to);
        long txn = transactionRepository.countInRange(from, to);
        return rate(cb, txn);
    }

    // -----------------------------------------------------------------------
    // Processor ranking (with per-processor chargeback rate)
    // -----------------------------------------------------------------------

    public ProcessorRankingResponse processorRanking(LocalDate from, LocalDate to) {
        List<Object[]> rows  = chargebackRepository.countByProcessorInRange(from, to);
        Map<String, Long> txnByProcessor = toCountMap(transactionRepository.countByProcessor(from, to));

        List<ProcessorRankingResponse.ProcessorStat> stats = rows.stream()
                .map(r -> {
                    String name = (String) r[0];
                    long cb     = ((Number) r[1]).longValue();
                    long txn    = txnByProcessor.getOrDefault(name, 0L);
                    return ProcessorRankingResponse.ProcessorStat.builder()
                            .processorName(name)
                            .chargebackCount(cb)
                            .transactionCount(txn)
                            .ratePercent(rate(cb, txn))
                            .totalAmount(toBigDecimal(r[2]))
                            .build();
                })
                .toList();

        log.debug("Processor ranking {} → {}: {} processors", from, to, stats.size());
        return ProcessorRankingResponse.builder()
                .from(from)
                .to(to)
                .overallRatePercent(overallRatePercent(from, to))
                .processors(stats)
                .build();
    }

    // -----------------------------------------------------------------------
    // BIN & Country clustering (with rate per BIN / per country)
    // -----------------------------------------------------------------------

    public BinClusteringResponse binClustering(LocalDate from, LocalDate to) {
        List<Object[]> binRows     = chargebackRepository.binClustering(from, to);
        List<Object[]> countryRows = chargebackRepository.countryClustering(from, to);

        Map<String, Long> txnByBin     = toCountMap(transactionRepository.countByBin(from, to));
        Map<String, Long> txnByCountry = toCountMap(transactionRepository.countByCountry(from, to));

        long totalCbs = countryRows.stream()
                .mapToLong(r -> ((Number) r[1]).longValue())
                .sum();

        List<BinClusteringResponse.BinStat> bins = binRows.stream()
                .map(r -> {
                    String bin   = (String) r[0];
                    long cb      = ((Number) r[2]).longValue();
                    long txn     = txnByBin.getOrDefault(bin, 0L);
                    return BinClusteringResponse.BinStat.builder()
                            .cardBin(bin)
                            .issuerCountry((String) r[1])
                            .chargebackCount(cb)
                            .transactionCount(txn)
                            .ratePercent(rate(cb, txn))
                            .totalAmount(toBigDecimal(r[3]))
                            .build();
                })
                .toList();

        List<BinClusteringResponse.CountryStat> countries = countryRows.stream()
                .map(r -> {
                    String country = (String) r[0];
                    long count     = ((Number) r[1]).longValue();
                    long txn       = txnByCountry.getOrDefault(country, 0L);
                    BigDecimal share = totalCbs > 0
                            ? BigDecimal.valueOf(count * 100.0 / totalCbs).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return BinClusteringResponse.CountryStat.builder()
                            .issuerCountry(country)
                            .chargebackCount(count)
                            .transactionCount(txn)
                            .ratePercent(rate(count, txn))
                            .totalAmount(toBigDecimal(r[2]))
                            .sharePercent(share)
                            .build();
                })
                .toList();

        return BinClusteringResponse.builder()
                .from(from)
                .to(to)
                .bins(bins)
                .countries(countries)
                .build();
    }

    // -----------------------------------------------------------------------
    // Reason code breakdown
    // -----------------------------------------------------------------------

    public ReasonCodeBreakdownResponse reasonCodeBreakdown(LocalDate from, LocalDate to) {
        List<Object[]> rows = chargebackRepository.reasonCodeBreakdown(from, to);

        long total = rows.stream().mapToLong(r -> ((Number) r[2]).longValue()).sum();

        List<ReasonCodeBreakdownResponse.ReasonStat> stats = rows.stream()
                .map(r -> {
                    long count = ((Number) r[2]).longValue();
                    BigDecimal share = total > 0
                            ? BigDecimal.valueOf(count * 100.0 / total).setScale(2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO;
                    return ReasonCodeBreakdownResponse.ReasonStat.builder()
                            .reasonCode((String) r[0])
                            .reasonCategory(r[1] != null ? r[1].toString() : "OTHER")
                            .chargebackCount(count)
                            .totalAmount(toBigDecimal(r[3]))
                            .sharePercent(share)
                            .build();
                })
                .toList();

        return ReasonCodeBreakdownResponse.builder()
                .from(from)
                .to(to)
                .totalChargebacks(total)
                .reasonCodes(stats)
                .build();
    }

    // -----------------------------------------------------------------------
    // MCC breakdown (with rate)
    // -----------------------------------------------------------------------

    public MccBreakdownResponse mccBreakdown(LocalDate from, LocalDate to) {
        List<Object[]> rows = chargebackRepository.mccBreakdown(from, to);
        Map<String, Long> txnByMcc = toCountMap(transactionRepository.countByMcc(from, to));

        List<MccBreakdownResponse.MccStat> stats = rows.stream()
                .map(r -> {
                    String mcc = (String) r[0];
                    long cb    = ((Number) r[1]).longValue();
                    long txn   = txnByMcc.getOrDefault(mcc, 0L);
                    return MccBreakdownResponse.MccStat.builder()
                            .mcc(mcc)
                            .mccDescription(MccDescriptions.describe(mcc))
                            .chargebackCount(cb)
                            .transactionCount(txn)
                            .ratePercent(rate(cb, txn))
                            .totalAmount(toBigDecimal(r[2]))
                            .build();
                })
                .toList();

        return MccBreakdownResponse.builder()
                .from(from)
                .to(to)
                .mccs(stats)
                .build();
    }

    // -----------------------------------------------------------------------
    // Chargeback rate trend + network threshold alert
    // -----------------------------------------------------------------------

    /**
     * Calculates the chargeback rate for each month in the range.
     * <p>
     * Formula: CBs in month X / Transactions in month X-1 × 100
     * This mirrors the Visa/Mastercard calculation methodology.
     */
    public ChargebackRateTrendResponse chargebackRateTrend(LocalDate from, LocalDate to) {
        List<ChargebackRateTrendResponse.MonthlyRate> monthlyRates = new ArrayList<>();
        BigDecimal latestRate = BigDecimal.ZERO;

        YearMonth current = YearMonth.from(from);
        YearMonth end     = YearMonth.from(to);

        while (!current.isAfter(end)) {
            LocalDate monthStart = current.atDay(1);

            long chargebacks = chargebackRepository.countChargebacksInMonth(monthStart);

            // Denominator = prior month's transaction count
            YearMonth priorMonth = current.minusMonths(1);
            long transactions = chargebackRepository.countTransactionsInMonth(priorMonth.atDay(1));

            BigDecimal ratePercent;
            if (transactions == 0) {
                ratePercent = BigDecimal.ZERO;
            } else {
                ratePercent = BigDecimal.valueOf((double) chargebacks / transactions * 100)
                        .setScale(4, RoundingMode.HALF_UP);
            }

            String riskLevel = classifyRisk(ratePercent);
            latestRate = ratePercent;

            monthlyRates.add(ChargebackRateTrendResponse.MonthlyRate.builder()
                    .month(current.format(DateTimeFormatter.ofPattern("yyyy-MM")))
                    .chargebackCount(chargebacks)
                    .transactionCount(transactions)
                    .ratePercent(ratePercent)
                    .riskLevel(riskLevel)
                    .build());

            current = current.plusMonths(1);
        }

        String overallRisk = classifyRisk(latestRate);
        String alert       = buildAlert(latestRate, overallRisk);

        log.info("Chargeback rate trend {} → {}: latest rate={}%, risk={}",
                from, to, latestRate, overallRisk);

        return ChargebackRateTrendResponse.builder()
                .from(from)
                .to(to)
                .months(monthlyRates)
                .overallRisk(overallRisk)
                .alert(alert)
                .build();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    public static String classifyRisk(BigDecimal rate) {
        if (rate.compareTo(THRESHOLD_RED) >= 0)   return "RED";
        if (rate.compareTo(THRESHOLD_AMBER) >= 0) return "AMBER";
        return "GREEN";
    }

    private String buildAlert(BigDecimal rate, String risk) {
        return switch (risk) {
            case "RED"   -> String.format(
                    "CRITICAL: Chargeback rate %.4f%% exceeds the 1.5%% network threshold. " +
                            "Immediate action required to avoid card acceptance termination.", rate);
            case "AMBER" -> String.format(
                    "WARNING: Chargeback rate %.4f%% is approaching the 1.5%% network threshold. " +
                            "Review high-risk BINs and reason codes.", rate);
            default      -> String.format(
                    "Chargeback rate %.4f%% is within acceptable limits.", rate);
        };
    }

    public static BigDecimal rate(long chargebacks, long transactions) {
        if (transactions <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf((double) chargebacks / transactions * 100)
                .setScale(4, RoundingMode.HALF_UP);
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] r : rows) {
            map.put((String) r[0], ((Number) r[1]).longValue());
        }
        return map;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
