package com.passlify.core.event;

import com.passlify.core.event.dto.CancelEventRequest;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.event.dto.EventAuditResponse;
import com.passlify.core.event.dto.EventResponse;
import com.passlify.core.event.dto.EventSummary;
import com.passlify.core.event.dto.PublicationReadinessResponse;
import com.passlify.core.event.dto.UpdateEventRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Organizer event management. Ownership is enforced in {@link EventService}. */
@RestController
@RequestMapping("/api/v1/events")
@PreAuthorize("isAuthenticated()")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventResponse create(@Valid @RequestBody CreateEventRequest req) {
        return EventResponse.from(eventService.create(req));
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable UUID id) {
        return EventResponse.from(eventService.getOwned(id));
    }

    @GetMapping
    public Page<EventSummary> list(@RequestParam(required = false) EventStatus status,
                                   @PageableDefault(size = 20) Pageable pageable) {
        return eventService.listOwned(status, pageable).map(EventSummary::from);
    }

    @PatchMapping("/{id}")
    public EventResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEventRequest req) {
        return EventResponse.from(eventService.update(id, req));
    }

    @GetMapping("/{id}/publication-readiness")
    public PublicationReadinessResponse publicationReadiness(@PathVariable UUID id) {
        return eventService.readiness(id);
    }

    @PostMapping("/{id}/publish")
    public EventResponse publish(@PathVariable UUID id) {
        return EventResponse.from(eventService.publish(id));
    }

    @PostMapping("/{id}/cancel")
    public EventResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelEventRequest req) {
        return EventResponse.from(eventService.cancel(id, req.reason()));
    }

    @PostMapping("/{id}/complete")
    public EventResponse complete(@PathVariable UUID id) {
        return EventResponse.from(eventService.complete(id));
    }

    @GetMapping("/{id}/audit")
    public Page<EventAuditResponse> audit(@PathVariable UUID id,
                                          @PageableDefault(size = 20) Pageable pageable) {
        return eventService.listAudit(id, pageable);
    }
}
