package yuno.challenge.analytics.controller;

import yuno.challenge.analytics.dto.*;
import yuno.challenge.analytics.dto.bin.BinClusteringResponse;
import yuno.challenge.analytics.dto.chargebackrate.ChargebackRateTrendResponse;
import yuno.challenge.analytics.dto.mccbreakdown.MccBreakdownResponse;
import yuno.challenge.analytics.dto.processor.ProcessorRankingResponse;
import yuno.challenge.analytics.dto.reasoncode.ReasonCodeBreakdownResponse;
import yuno.challenge.analytics.service.AnalyticsService;
import yuno.challenge.common.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * GET /api/analytics/processors?from=2025-01-01&to=2025-03-31
     *
     * "Which processor has the highest chargeback rate this month?"
     * Returns chargeback count and total amount per processor, ranked by volume.
     */
    @GetMapping("/processors")
    public ResponseEntity<ApiResponse<ProcessorRankingResponse>> processorRanking(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateRange(from, to);
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.processorRanking(from, to)));
    }

    /**
     * GET /api/analytics/bins?from=2025-01-01&to=2025-03-31
     *
     * "Are chargebacks clustering around specific BINs or countries?"
     * Returns top BINs and country breakdown.
     */
    @GetMapping("/bins")
    public ResponseEntity<ApiResponse<BinClusteringResponse>> binClustering(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateRange(from, to);
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.binClustering(from, to)));
    }

    /**
     * GET /api/analytics/reason-codes?from=2025-01-01&to=2025-03-31
     *
     * "Which reason codes are driving the spike — fraud or fulfillment issues?"
     * Returns reason code counts with normalized categories and share percentages.
     */
    @GetMapping("/reason-codes")
    public ResponseEntity<ApiResponse<ReasonCodeBreakdownResponse>> reasonCodeBreakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateRange(from, to);
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.reasonCodeBreakdown(from, to)));
    }

    /**
     * GET /api/analytics/rate?from=2025-01-01&to=2025-03-31
     *
     * "What's our current chargeback rate trend, and are we at risk of exceeding network thresholds?"
     * Calculates monthly rates using the Visa/Mastercard formula (CBs in M / Txns in M-1).
     * Includes risk classification: GREEN / AMBER / RED and a human-readable alert.
     */
    @GetMapping("/rate")
    public ResponseEntity<ApiResponse<ChargebackRateTrendResponse>> chargebackRate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateRange(from, to);
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.chargebackRateTrend(from, to)));
    }

    /**
     * GET /api/analytics/mcc?from=2025-01-01&to=2025-03-31
     *
     * "Are chargebacks clustering around specific product categories (MCC)?"
     * Returns chargeback volume per Merchant Category Code with human-readable descriptions.
     */
    @GetMapping("/mcc")
    public ResponseEntity<ApiResponse<MccBreakdownResponse>> mccBreakdown(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateRange(from, to);
        return ResponseEntity.ok(ApiResponse.ok(analyticsService.mccBreakdown(from, to)));
    }

    private void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "'from' date must be before or equal to 'to' date");
        }
        if (from.plusYears(2).isBefore(to)) {
            throw new IllegalArgumentException(
                    "Date range cannot exceed 2 years");
        }
    }
}
