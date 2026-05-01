package yuno.challenge.alert.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import yuno.challenge.alert.dto.AlertReport;
import yuno.challenge.alert.service.AlertService;
import yuno.challenge.common.response.ApiResponse;

import java.time.LocalDate;

@Slf4j
@RestController
@RequestMapping("/api/alerts")
@RequiredArgsConstructor
public class AlertController {

    private final AlertService alertService;

    /**
     * GET /api/alerts?from=2025-01-01&to=2025-04-30
     * <p>
     * Triggers all pattern-detection rules and returns the consolidated alert report.
     * Rules:
     *   1. OVERALL_RATE_CRITICAL / WARNING — overall rate vs network thresholds
     *   2. PROCESSOR_HOTSPOT — processor with rate > 2x portfolio average
     *   3. BIN_CLUSTER — single BIN with ≥ 8 disputes
     *   4. REASON_CODE_SPIKE — single category accounts for ≥ 60% of disputes
     *   5. GEO_CONCENTRATION — country with ≥40% chargebacks but ≤20% txns
     */
    @GetMapping
    public ResponseEntity<ApiResponse<AlertReport>> runAlerts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be before or equal to 'to'");
        }
        AlertReport report = alertService.runAllRules(from, to);
        return ResponseEntity.ok(ApiResponse.ok(
                "Pattern detection complete: " + report.getTotalAlerts() + " alert(s)",
                report));
    }
}
