package com.passlify.core.event;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.dto.PublicEventDetail;
import com.passlify.core.event.dto.PublicEventSummary;
import com.passlify.core.forms.CustomFieldService;
import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated buyer catalog (open in SecurityConfig). */
@RestController
@RequestMapping("/api/v1/public/events")
public class PublicEventController {

    private final PublicCatalogService catalog;
    private final CustomFieldService customFieldService;

    public PublicEventController(PublicCatalogService catalog, CustomFieldService customFieldService) {
        this.catalog = catalog;
        this.customFieldService = customFieldService;
    }

    @GetMapping
    public Page<PublicEventSummary> browse(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) UUID eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "false") boolean includePast,
            @PageableDefault(size = 20) Pageable pageable) {
        return catalog.search(q, city, eventType, from, to, includePast, pageable).map(PublicEventSummary::from);
    }

    @GetMapping("/{slug}")
    public ResponseEntity<PublicEventDetail> detail(@PathVariable String slug,
                                                    @RequestParam(required = false) String access) {
        Optional<Event> current = catalog.findAccessibleBySlug(slug, access);
        if (current.isPresent()) {
            Event event = current.get();
            return ResponseEntity.ok(PublicEventDetail.from(
                    event,
                    catalog.publicTicketTypes(event.getId()),
                    customFieldService.publicList(event.getId())));
        }
        // A retired slug → permanent redirect to the event's current slug.
        return catalog.findRedirectTargetSlug(slug)
                .map(newSlug -> ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                        .location(URI.create("/api/v1/public/events/" + newSlug))
                        .<PublicEventDetail>build())
                .orElseThrow(() -> ApiException.notFound("Event not found: " + slug));
    }
}
