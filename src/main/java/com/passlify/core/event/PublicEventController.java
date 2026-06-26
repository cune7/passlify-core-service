package com.passlify.core.event;

import com.passlify.core.event.dto.PublicEventDetail;
import com.passlify.core.event.dto.PublicEventSummary;
import com.passlify.core.forms.CustomFieldService;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
            @PageableDefault(size = 20) Pageable pageable) {
        return catalog.search(q, city, eventType, from, to, pageable).map(PublicEventSummary::from);
    }

    @GetMapping("/{slug}")
    public PublicEventDetail detail(@PathVariable String slug) {
        Event event = catalog.getPublishedBySlug(slug);
        return PublicEventDetail.from(
                event,
                catalog.publicTicketTypes(event.getId()),
                customFieldService.publicList(event.getId()));
    }
}
