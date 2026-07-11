package com.passlify.core.event;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Automatically completes events that have ended (EVENT_DOMAIN_SPEC §7.4). Runs
 * periodically: any {@code PUBLISHED} event whose {@code endsAt} + grace has passed is
 * moved to {@code COMPLETED} (audited as a system action, emitting EventCompletedEvent).
 * Grace defaults to the platform setting; an admin may override it per event
 * ({@code Event.autoCompleteGraceHours}). Idempotent; DRAFT/CANCELLED are never touched.
 */
@Service
public class EventCompletionService {

    private static final Logger log = LoggerFactory.getLogger(EventCompletionService.class);

    private final EventRepository events;
    private final EventService eventService;
    private final int defaultGraceHours;

    public EventCompletionService(EventRepository events,
                                  EventService eventService,
                                  @Value("${passlify.event.auto-complete-grace-hours:24}") int defaultGraceHours) {
        this.events = events;
        this.eventService = eventService;
        this.defaultGraceHours = defaultGraceHours;
    }

    @Scheduled(fixedDelayString = "${passlify.event.auto-complete-interval-ms:3600000}")
    public void completeEndedEvents() {
        Instant now = Instant.now();
        List<Event> candidates = events.findByStatusAndEndsAtBefore(EventStatus.PUBLISHED, now);
        int completed = 0;
        for (Event e : candidates) {
            if (now.isAfter(e.getEndsAt().plus(graceHours(e), ChronoUnit.HOURS))) {
                try {
                    eventService.autoComplete(e.getId());
                    completed++;
                } catch (RuntimeException ex) {
                    log.warn("Auto-complete failed for event {}: {}", e.getId(), ex.getMessage());
                }
            }
        }
        if (completed > 0) {
            log.info("Auto-completed {} ended event(s)", completed);
        }
    }

    private int graceHours(Event e) {
        return e.getAutoCompleteGraceHours() != null ? e.getAutoCompleteGraceHours() : defaultGraceHours;
    }
}
