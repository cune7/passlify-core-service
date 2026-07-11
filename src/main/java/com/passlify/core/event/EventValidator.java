package com.passlify.core.event;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.organization.OrganizationValidator;
import com.passlify.core.payment.PaymentCapabilityService;
import com.passlify.core.ticket.TicketTypeRepository;
import java.time.Instant;
import org.springframework.stereotype.Component;

/**
 * All event precondition checks: date sanity and the publish/cancel lifecycle
 * guards (DOMAIN §2.3). Publishing is one-way — there is no unpublish. Injected
 * into {@link EventService}, which keeps loading, ownership and state mutation.
 */
@Component
public class EventValidator {

    private final TicketTypeRepository ticketTypes;
    private final OrganizationValidator organizationValidator;
    private final PaymentCapabilityService paymentCapabilities;

    public EventValidator(TicketTypeRepository ticketTypes,
                          OrganizationValidator organizationValidator,
                          PaymentCapabilityService paymentCapabilities) {
        this.ticketTypes = ticketTypes;
        this.organizationValidator = organizationValidator;
        this.paymentCapabilities = paymentCapabilities;
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
        // Commercial mode is explicit (§9), not inferred from price. Paid events are
        // B2B: the organizer must have a complete company profile and a real provider.
        if (e.getCommercialMode() == CommercialMode.PAID) {
            organizationValidator.assertCanSellPaidEvents(e.getOrganizerId());
            if (e.getPaymentProvider() == com.passlify.core.payment.PaymentProvider.NONE) {
                throw ApiException.invalidState("A paid event requires a payment provider");
            }
            // Real processors need an admin-approved, currency-covering capability.
            if (e.getPaymentProvider().requiresCapability()
                    && !paymentCapabilities.isUsable(e.getOrganizationId(), e.getPaymentProvider(), e.getCurrency())) {
                throw ApiException.invalidState(
                        "Payment provider " + e.getPaymentProvider() + " is not approved for this organization"
                                + " in " + e.getCurrency());
            }
        }
    }

    public void assertCancellable(Event e) {
        if (e.getStatus() == EventStatus.COMPLETED) {
            throw ApiException.invalidState("Cannot cancel a completed event");
        }
    }

    /** Only a published event completes; cancelled/draft/already-completed cannot (§24.3). */
    public void assertCompletable(Event e) {
        if (e.getStatus() != EventStatus.PUBLISHED) {
            throw ApiException.invalidState("Only a published event can be completed");
        }
    }
}
