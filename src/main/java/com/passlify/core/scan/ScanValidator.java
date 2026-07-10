package com.passlify.core.scan;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.Event;
import com.passlify.core.event.EventRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Scan preconditions. Injected into {@link ScanService}; the QR-signature, ticket
 * status and concurrency handling stay in the service (they drive the audited
 * allow/deny verdict, which is control flow rather than a simple guard).
 */
@Component
public class ScanValidator {

    private final EventRepository events;

    public ScanValidator(EventRepository events) {
        this.events = events;
    }

    /** The event being scanned for (or reported on) must exist. */
    public Event requireEvent(UUID eventId) {
        return events.findById(eventId)
                .orElseThrow(() -> ApiException.notFound("Event not found: " + eventId));
    }
}
