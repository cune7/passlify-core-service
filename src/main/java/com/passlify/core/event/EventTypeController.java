package com.passlify.core.event;

import com.passlify.core.event.dto.EventTypeCatalogEntry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public read-only catalog of active event types (categories + selectable leaves),
 * used by organizers to classify an event (EVENT_DOMAIN_SPEC §19). Under
 * {@code /api/v1/public} so it is reachable without authentication.
 */
@RestController
@RequestMapping("/api/v1/public/event-types")
public class EventTypeController {

    private final EventTypeRepository eventTypes;

    public EventTypeController(EventTypeRepository eventTypes) {
        this.eventTypes = eventTypes;
    }

    @GetMapping
    public List<EventTypeCatalogEntry> list() {
        return eventTypes.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .map(EventTypeCatalogEntry::from)
                .toList();
    }
}
