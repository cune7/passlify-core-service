package com.passlify.core.event;

import com.passlify.core.event.dto.AccessGrantResponse;
import com.passlify.core.event.dto.CreateAccessGrantRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Access grants for PRIVATE events (EVENT_DOMAIN_SPEC §8): create/list/revoke shareable
 * tokens that let invitees view + buy the event. Managed by owner/manager/admin.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/access-grants")
@PreAuthorize("isAuthenticated()")
public class EventAccessController {

    private final EventAccessService access;

    public EventAccessController(EventAccessService access) {
        this.access = access;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccessGrantResponse create(@PathVariable UUID eventId,
                                      @Valid @RequestBody(required = false) CreateAccessGrantRequest req) {
        return access.create(eventId, req == null ? new CreateAccessGrantRequest(null) : req);
    }

    @GetMapping
    public List<AccessGrantResponse> list(@PathVariable UUID eventId) {
        return access.list(eventId);
    }

    @DeleteMapping("/{grantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID eventId, @PathVariable UUID grantId) {
        access.revoke(eventId, grantId);
    }
}
