package com.passlify.core.support;

import com.passlify.core.event.EventContactService;
import com.passlify.core.event.dto.EventContactRequest;
import com.passlify.core.event.dto.LocationDto;
import java.util.UUID;

/**
 * Shared test helpers for the publish preconditions added in EVENT_DOMAIN_SPEC §14/§18:
 * publishing now requires a physical location (for IN_PERSON/HYBRID events) and at least
 * one contact method. Put the location in at {@code create} time (a single audit entry)
 * and set the contact via {@link EventContactService}, which writes no audit entry — so
 * audit-asserting tests stay clean.
 */
public final class EventFixtures {

    private EventFixtures() {}

    /** A ready-to-publish physical location for IN_PERSON/HYBRID event fixtures. */
    public static final LocationDto TEST_LOCATION =
            new LocationDto("Test Venue", "Main St 1", "Belgrade", "RS", "11000");

    /** Give an event a contact method so it clears the §18.2 publish precondition. */
    public static void addContact(EventContactService contacts, UUID eventId) {
        contacts.update(eventId, new EventContactRequest(
                "info@test.rs", null, null, null, null, null, null, null, null,
                null, null, null, null));
    }
}
