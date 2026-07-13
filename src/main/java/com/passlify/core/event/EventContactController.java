package com.passlify.core.event;

import com.passlify.core.event.dto.EventContactRequest;
import com.passlify.core.event.dto.EventContactResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Contact and social links for an event (EVENT_DOMAIN_SPEC §18). */
@RestController
@RequestMapping("/api/v1/events/{eventId}/contact")
@PreAuthorize("isAuthenticated()")
public class EventContactController {

    private final EventContactService contact;

    public EventContactController(EventContactService contact) {
        this.contact = contact;
    }

    @GetMapping
    public EventContactResponse get(@PathVariable UUID eventId) {
        return contact.get(eventId);
    }

    @PutMapping
    public EventContactResponse update(@PathVariable UUID eventId,
                                       @Valid @RequestBody EventContactRequest req) {
        return contact.update(eventId, req);
    }
}
