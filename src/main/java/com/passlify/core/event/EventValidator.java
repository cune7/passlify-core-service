package com.passlify.core.event;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.organization.OrganizationValidator;
import com.passlify.core.ticket.TicketTypeRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * All event precondition checks: date sanity and the publish/unpublish/cancel
 * lifecycle guards (DOMAIN §2.3). Injected into {@link EventService}, which keeps
 * loading, ownership and state mutation.
 */
@Component
public class EventValidator {

    private final TicketTypeRepository ticketTypes;
    private final OrganizationValidator organizationValidator;

    public EventValidator(TicketTypeRepository ticketTypes, OrganizationValidator organizationValidator) {
        this.ticketTypes = ticketTypes;
        this.organizationValidator = organizationValidator;
    }

    public void validateDates(Instant startsAt, Instant endsAt) {
        if (!endsAt.isAfter(startsAt)) {
            throw ApiException.validation("endsAt must be after startsAt");
        }
    }

    /**
     * A publishable event: not already published/cancelled/completed, starts in the
     * future, has at least one active ticket type, and — if any active ticket type is
     * priced — the organizer is a billable company.
     */
    public void assertPublishable(Event e) {
        if (e.getStatus() == EventStatus.PUBLISHED) {
            throw ApiException.of(ErrorCode.ALREADY_PUBLISHED, "Event is already published");
        }
        if (e.getStatus() == EventStatus.CANCELLED || e.getStatus() == EventStatus.COMPLETED) {
            throw ApiException.invalidState("Cannot publish a " + e.getStatus() + " event");
        }
        if (e.getStartsAt().isBefore(Instant.now())) {
            throw ApiException.invalidState("Cannot publish an event that has already started");
        }
        if (ticketTypes.countByEventIdAndActiveTrue(e.getId()) == 0) {
            throw ApiException.invalidState("Event needs at least one active ticket type to publish");
        }
        // Paid events are B2B: the organizer must have a complete company profile.
        if (ticketTypes.existsByEventIdAndActiveTrueAndPriceMinorGreaterThan(e.getId(), 0L)) {
            organizationValidator.assertCanSellPaidEvents(e.getOrganizerId());
        }
    }

    public void assertUnpublishable(Event e) {
        if (e.getStatus() != EventStatus.PUBLISHED) {
            throw ApiException.invalidState("Only a published event can be unpublished");
        }
    }

    public void assertCancellable(Event e) {
        if (e.getStatus() == EventStatus.COMPLETED) {
            throw ApiException.invalidState("Cannot cancel a completed event");
        }
    }
}
