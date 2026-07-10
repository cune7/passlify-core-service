package com.passlify.core.dashboard;

import com.passlify.core.dashboard.dto.AttendeeRow;
import com.passlify.core.dashboard.dto.OrderSummary;
import com.passlify.core.dashboard.dto.SalesSummaryResponse;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Organizer dashboard reads for one event (ownership enforced in the service). */
@RestController
@RequestMapping("/api/v1/events/{eventId}")
@PreAuthorize("isAuthenticated()")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/orders")
    public Page<OrderSummary> orders(@PathVariable UUID eventId,
                                     @PageableDefault(size = 20) Pageable pageable) {
        return dashboardService.listOrders(eventId, pageable);
    }

    @GetMapping("/attendees")
    public Page<AttendeeRow> attendees(@PathVariable UUID eventId,
                                       @PageableDefault(size = 50) Pageable pageable) {
        return dashboardService.listAttendees(eventId, pageable);
    }

    @GetMapping("/sales-summary")
    public SalesSummaryResponse salesSummary(@PathVariable UUID eventId) {
        return dashboardService.salesSummary(eventId);
    }

    @GetMapping(value = "/attendees/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportAttendees(@PathVariable UUID eventId) {
        byte[] csv = dashboardService.attendeesCsv(eventId).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"attendees-" + eventId + ".csv\"")
                .body(csv);
    }
}
