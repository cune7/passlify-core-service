package com.passlify.core.event;

import com.passlify.core.event.dto.EventResponse;
import com.passlify.core.event.dto.SetAutoCompleteGraceRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only event controls. */
@RestController
@RequestMapping("/api/v1/admin/events")
@PreAuthorize("hasRole('ADMIN')")
public class AdminEventController {

    private final EventService eventService;

    public AdminEventController(EventService eventService) {
        this.eventService = eventService;
    }

    /** Override (or clear, with null) the per-event auto-completion grace (§7.4). */
    @PutMapping("/{eventId}/auto-complete-grace")
    public EventResponse setAutoCompleteGrace(@PathVariable UUID eventId,
                                              @Valid @RequestBody SetAutoCompleteGraceRequest req) {
        return EventResponse.from(eventService.setAutoCompleteGrace(eventId, req.graceHours()));
    }
}
