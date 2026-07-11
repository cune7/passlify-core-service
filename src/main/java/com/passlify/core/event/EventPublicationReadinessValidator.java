package com.passlify.core.event;

import com.passlify.core.event.dto.PublicationReadinessResponse;
import com.passlify.core.event.dto.PublicationReadinessResponse.Violation;
import com.passlify.core.organization.OrganizationValidator;
import com.passlify.core.payment.PaymentProvider;
import com.passlify.core.ticket.TicketTypeRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Computes the publication readiness of an event as a structured list of violations
 * (EVENT_DOMAIN_SPEC §23). Non-throwing: used both for the {@code GET
 * /publication-readiness} preview and as the completeness checklist the UI renders
 * before an organizer attempts to publish.
 */
@Component
public class EventPublicationReadinessValidator {

    private final TicketTypeRepository ticketTypes;
    private final EventOnlineAccessRepository onlineAccess;
    private final OrganizationValidator organizationValidator;

    public EventPublicationReadinessValidator(TicketTypeRepository ticketTypes,
                                              EventOnlineAccessRepository onlineAccess,
                                              OrganizationValidator organizationValidator) {
        this.ticketTypes = ticketTypes;
        this.onlineAccess = onlineAccess;
        this.organizationValidator = organizationValidator;
    }

    public PublicationReadinessResponse check(Event e) {
        List<Violation> v = new ArrayList<>();

        if (isBlank(e.getName())) {
            v.add(new Violation("NAME_REQUIRED", "name", "Event name is required."));
        }
        if (e.getEventType() == null) {
            v.add(new Violation("EVENT_TYPE_REQUIRED", "eventType", "An event type must be selected."));
        }
        checkAttendance(e, v);
        if (e.getContact() == null || !e.getContact().hasAnyMethod()) {
            v.add(new Violation("CONTACT_REQUIRED", "contact",
                    "At least one event contact method (email, phone or website) is required."));
        }

        long activeTicketTypes = ticketTypes.countByEventIdAndActiveTrue(e.getId());
        if (activeTicketTypes == 0) {
            v.add(new Violation("TICKET_TYPE_REQUIRED", "ticketTypes",
                    "The event needs at least one active ticket type."));
        }
        boolean hasPricedTicket = ticketTypes.existsByEventIdAndActiveTrueAndPriceMinorGreaterThan(e.getId(), 0L);
        checkCommercial(e, v, activeTicketTypes, hasPricedTicket);
        checkCapacity(e, v);

        return new PublicationReadinessResponse(v.isEmpty(), v);
    }

    private void checkAttendance(Event e, List<Violation> v) {
        AttendanceMode mode = e.getAttendanceMode();
        boolean needsLocation = mode == AttendanceMode.IN_PERSON || mode == AttendanceMode.HYBRID;
        boolean needsOnline = mode == AttendanceMode.ONLINE || mode == AttendanceMode.HYBRID;
        if (needsLocation && e.getLocation() == null) {
            v.add(new Violation("LOCATION_REQUIRED", "location",
                    "A " + mode + " event requires a physical location."));
        }
        if (needsOnline && !onlineConfigured(e)) {
            v.add(new Violation("ONLINE_ACCESS_REQUIRED", "onlineAccess",
                    "A " + mode + " event requires online-access configuration."));
        }
    }

    private void checkCommercial(Event e, List<Violation> v, long activeTicketTypes, boolean hasPricedTicket) {
        if (e.getCommercialMode() == CommercialMode.PAID) {
            if (!organizationValidator.canSellPaidEvents(e.getOrganizerId())) {
                v.add(new Violation("EVENT_COMPANY_REQUIRED", "organization",
                        "Paid events require a complete COMPANY organization profile."));
            }
            if (e.getPaymentProvider() == PaymentProvider.NONE) {
                v.add(new Violation("PAYMENT_PROVIDER_REQUIRED", "paymentProvider",
                        "A paid event requires a payment provider."));
            }
            if (activeTicketTypes > 0 && !hasPricedTicket) {
                v.add(new Violation("SELLABLE_TICKET_REQUIRED", "ticketTypes",
                        "A paid event needs at least one priced ticket type."));
            }
        } else if (hasPricedTicket) {
            v.add(new Violation("FREE_EVENT_HAS_PAID_TICKETS", "ticketTypes",
                    "A free event cannot have priced ticket types."));
        }
    }

    private void checkCapacity(Event e, List<Violation> v) {
        if (e.getCapacity() != null && ticketTypes.sumActiveTotalQuantity(e.getId()) > e.getCapacity()) {
            v.add(new Violation("CAPACITY_EXCEEDED", "capacity",
                    "Total ticket-type quantity exceeds the event capacity."));
        }
    }

    private boolean onlineConfigured(Event e) {
        return onlineAccess.findById(e.getId()).map(EventOnlineAccess::isConfigured).orElse(false);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
