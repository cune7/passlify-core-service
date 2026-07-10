package com.passlify.core.order;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.event.Event;
import com.passlify.core.event.EventStatus;
import com.passlify.core.forms.CustomField;
import com.passlify.core.order.dto.CreateOrderRequest;
import com.passlify.core.ticket.TicketType;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * All checkout precondition checks (DOMAIN §4.1–4.2, master spec §13): no duplicate
 * lines, single-event orders, sellability windows/limits, attendee counts and
 * custom-field values. Pure — operates on the data the {@link CheckoutService}
 * passes in; inventory reservation and persistence stay in the service.
 */
@Component
public class CheckoutValidator {

    /** An order may not list the same ticket type twice (quantities must be combined). */
    public void rejectDuplicateTicketTypes(List<CreateOrderRequest.Line> lines) {
        Set<UUID> seen = new HashSet<>();
        for (CreateOrderRequest.Line line : lines) {
            if (!seen.add(line.ticketTypeId())) {
                throw ApiException.validation(
                        "Duplicate ticket type in order: " + line.ticketTypeId()
                                + " (combine into a single line)");
            }
        }
    }

    /** All items in an order must belong to the same event. */
    public void assertSameEvent(Event expected, Event candidate) {
        if (!expected.getId().equals(candidate.getId())) {
            throw ApiException.of(ErrorCode.NOT_SELLABLE, "All items must belong to the same event");
        }
    }

    /** The ticket type must be on sale (published, active, in window) and within per-order limits. */
    public void assertSellable(TicketType tt, int quantity) {
        if (tt.getEvent().getStatus() != EventStatus.PUBLISHED) {
            throw ApiException.of(ErrorCode.NOT_SELLABLE, "Event is not on sale");
        }
        if (!tt.isActive()) {
            throw ApiException.of(ErrorCode.NOT_SELLABLE, "Ticket type '" + tt.getName() + "' is not active");
        }
        Instant now = Instant.now();
        if (tt.getSalesStartAt() != null && now.isBefore(tt.getSalesStartAt())) {
            throw ApiException.of(ErrorCode.NOT_SELLABLE, "Sales have not started for '" + tt.getName() + "'");
        }
        if (tt.getSalesEndAt() != null && now.isAfter(tt.getSalesEndAt())) {
            throw ApiException.of(ErrorCode.NOT_SELLABLE, "Sales have ended for '" + tt.getName() + "'");
        }
        if (quantity > tt.getMaxPerOrder()) {
            throw ApiException.of(ErrorCode.QTY_EXCEEDS_MAX,
                    "Max " + tt.getMaxPerOrder() + " per order for '" + tt.getName() + "'");
        }
    }

    /** EACH_TICKET types require exactly one attendee per quantity unit. */
    public void assertAttendeeCount(TicketType tt, List<CreateOrderRequest.Attendee> provided, int quantity) {
        if (provided == null || provided.size() != quantity) {
            throw ApiException.validation("Ticket type '" + tt.getName()
                    + "' requires attendee details for each ticket (" + quantity + " expected)");
        }
    }

    /**
     * Keeps only recognized keys and enforces required fields, returning the cleaned map.
     * {@code context} labels the error ("buyer" / "attendee").
     */
    public Map<String, String> cleanAndValidate(List<CustomField> defs, Map<String, String> submitted, String context) {
        Map<String, String> safe = submitted != null ? submitted : Map.of();
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (CustomField def : defs) {
            String value = safe.get(def.getFieldKey());
            if (def.isRequired() && (value == null || value.isBlank())) {
                throw ApiException.validation(
                        "Missing required " + context + " field: " + def.getLabel() + " (" + def.getFieldKey() + ")");
            }
            if (value != null && !value.isBlank()) {
                cleaned.put(def.getFieldKey(), value);
            }
        }
        return cleaned;
    }
}
