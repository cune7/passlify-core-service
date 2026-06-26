package com.passlify.core.scan;

import com.passlify.core.common.security.CurrentUser;
import com.passlify.core.scan.dto.ScanRequest;
import com.passlify.core.scan.dto.ScanResponse;
import com.passlify.core.scan.dto.ScanSummaryResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Gate operator scanning + live counts. */
@RestController
public class ScanController {

    private final ScanService scanService;
    private final CurrentUser currentUser;

    public ScanController(ScanService scanService, CurrentUser currentUser) {
        this.scanService = scanService;
        this.currentUser = currentUser;
    }

    /** Validate & redeem a ticket. Always 200 — a denial is a verdict, not an error. */
    @PostMapping("/api/v1/scan")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ScanResponse scan(@Valid @RequestBody ScanRequest req) {
        return scanService.scan(req.qrToken(), req.eventId(), req.gate(), currentUser.requireSubject());
    }

    @GetMapping("/api/v1/events/{id}/scan-summary")
    @PreAuthorize("hasAnyRole('OPERATOR','ORGANIZER','ADMIN')")
    public ScanSummaryResponse summary(@PathVariable UUID id) {
        return scanService.summary(id);
    }
}
