package com.passlify.core.event;

import com.passlify.core.event.dto.EventOnlineAccessRequest;
import com.passlify.core.event.dto.EventOnlineAccessResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Online-access configuration for ONLINE / HYBRID events. */
@RestController
@RequestMapping("/api/v1/events/{eventId}/online-access")
@PreAuthorize("isAuthenticated()")
public class EventOnlineAccessController {

    private final EventOnlineAccessService access;

    public EventOnlineAccessController(EventOnlineAccessService access) {
        this.access = access;
    }

    @GetMapping
    public EventOnlineAccessResponse get(@PathVariable UUID eventId) {
        return access.get(eventId);
    }

    @PutMapping
    public EventOnlineAccessResponse update(@PathVariable UUID eventId,
                                            @Valid @RequestBody EventOnlineAccessRequest req) {
        return access.update(eventId, req);
    }
}
