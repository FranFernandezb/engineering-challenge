package yuno.challenge.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import yuno.challenge.common.response.ApiResponse;

@Slf4j
@RestController
@RequestMapping("/api/seed")
@RequiredArgsConstructor
public class SeedController {

    private final SeedService seedService;

    /**
     * POST /api/seed/load — loads the bundled demo CSVs from /data/.
     * Idempotent: re-running counts duplicates instead of failing.
     */
    @PostMapping("/load")
    public ResponseEntity<ApiResponse<SeedService.SeedReport>> seed() {
        log.info("Seeding demo data...");
        SeedService.SeedReport report = seedService.loadAll();
        return ResponseEntity.ok(ApiResponse.ok("Seed complete", report));
    }
}
