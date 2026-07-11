package com.passlify.core.event;

import com.passlify.core.event.dto.EventSettingsRequest;
import com.passlify.core.event.dto.EventSettingsResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Structured event settings (age, entry, country restriction, rules). */
@RestController
@RequestMapping("/api/v1/events/{eventId}/settings")
@PreAuthorize("isAuthenticated()")
public class EventSettingsController {

    private final EventSettingsService settings;

    public EventSettingsController(EventSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public EventSettingsResponse get(@PathVariable UUID eventId) {
        return settings.get(eventId);
    }

    @PutMapping
    public EventSettingsResponse update(@PathVariable UUID eventId,
                                        @Valid @RequestBody EventSettingsRequest req) {
        return settings.update(eventId, req);
    }
}
