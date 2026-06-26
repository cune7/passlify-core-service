package com.passlify.core.order;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.common.security.CurrentUser;
import com.passlify.core.event.Event;
import com.passlify.core.event.EventStatus;
import com.passlify.core.forms.CustomField;
import com.passlify.core.forms.CustomFieldRepository;
import com.passlify.core.forms.FieldScope;
import com.passlify.core.issuance.TicketIssuanceService;
import com.passlify.core.order.dto.CreateOrderRequest;
import com.passlify.core.ticket.AttendeeDataMode;
import com.passlify.core.ticket.TicketType;
import com.passlify.core.ticket.TicketTypeRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Checkout: server-authoritative pricing + atomic inventory reservation (DOMAIN
 * §4.1–4.2), plus custom-field/attendee capture (master spec §13). The client
 * never sends prices. Inventory is reserved with a conditional UPDATE per line; if
 * any line is sold out the whole transaction rolls back. All items must belong to
 * one event (fixes the order's currency and payment provider). For EACH_TICKET
 * ticket types, one attendee per quantity unit is required and persisted; PER_*
 * custom fields are validated against the event's definitions.
 */
@Service
public class CheckoutService {

    /** How long a checkout holds inventory before the expirer releases it. */
    static final Duration RESERVATION_TTL = Duration.ofMinutes(15);

    private final OrderRepository orders;
    private final TicketTypeRepository ticketTypes;
    private final CustomFieldRepository customFields;
    private final AttendeeRepository attendees;
    private final CurrentUser currentUser;
    private final TicketIssuanceService ticketIssuanceService;
    private final ObjectMapper objectMapper;

    public CheckoutService(OrderRepository orders,
                           TicketTypeRepository ticketTypes,
                           CustomFieldRepository customFields,
                           AttendeeRepository attendees,
                           CurrentUser currentUser,
                           TicketIssuanceService ticketIssuanceService,
                           ObjectMapper objectMapper) {
        this.orders = orders;
        this.ticketTypes = ticketTypes;
        this.customFields = customFields;
        this.attendees = attendees;
        this.currentUser = currentUser;
        this.ticketIssuanceService = ticketIssuanceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest req) {
        List<CreateOrderRequest.Line> lines = req.items();
        rejectDuplicateTicketTypes(lines);

        // Reserve in a stable order (by id) to avoid deadlocks under concurrency.
        List<CreateOrderRequest.Line> ordered = new ArrayList<>(lines);
        ordered.sort(Comparator.comparing(CreateOrderRequest.Line::ticketTypeId));

        Order order = new Order();
        Event event = null;
        long subtotal = 0;
        List<PendingAttendee> pendingAttendees = new ArrayList<>();

        for (CreateOrderRequest.Line line : ordered) {
            TicketType tt = ticketTypes.findById(line.ticketTypeId())
                    .orElseThrow(() -> ApiException.notFound("Ticket type not found: " + line.ticketTypeId()));
            Event ttEvent = tt.getEvent();

            if (event == null) {
                event = ttEvent;
            } else if (!event.getId().equals(ttEvent.getId())) {
                throw ApiException.of(ErrorCode.NOT_SELLABLE, "All items must belong to the same event");
            }

            assertSellable(tt, line.quantity());

            if (tt.getAttendeeDataMode() == AttendeeDataMode.EACH_TICKET) {
                List<CreateOrderRequest.Attendee> provided = line.attendees();
                if (provided == null || provided.size() != line.quantity()) {
                    throw ApiException.validation("Ticket type '" + tt.getName()
                            + "' requires attendee details for each ticket (" + line.quantity() + " expected)");
                }
                for (CreateOrderRequest.Attendee a : provided) {
                    pendingAttendees.add(new PendingAttendee(tt, a));
                }
            }

            // Atomic reservation — fails the whole order if sold out.
            int reserved = ticketTypes.reserve(tt.getId(), line.quantity());
            if (reserved == 0) {
                throw ApiException.of(ErrorCode.SOLD_OUT,
                        "Ticket type '" + tt.getName() + "' does not have " + line.quantity() + " left");
            }

            long lineTotal = tt.getPriceMinor() * line.quantity();
            subtotal += lineTotal;

            OrderItem item = new OrderItem();
            item.setTicketType(tt);
            item.setQuantity(line.quantity());
            item.setUnitPriceMinor(tt.getPriceMinor());
            item.setTotalPriceMinor(lineTotal);
            order.addItem(item);
        }

        // Custom field validation (event is now known).
        UUID eventId = event.getId();
        List<CustomField> purchaseDefs =
                customFields.findByEventIdAndScopeOrderBySortOrderAscCreatedAtAsc(eventId, FieldScope.PER_PURCHASE);
        Map<String, String> buyerValues = cleanAndValidate(purchaseDefs, req.buyer().fields(), "buyer");

        List<CustomField> attendeeDefs =
                customFields.findByEventIdAndScopeOrderBySortOrderAscCreatedAtAsc(eventId, FieldScope.PER_ATTENDEE);
        for (PendingAttendee pending : pendingAttendees) {
            pending.cleanedFields = cleanAndValidate(attendeeDefs, pending.input.fields(), "attendee");
        }

        boolean free = subtotal == 0;
        order.setStatus(free ? OrderStatus.PAID : OrderStatus.PENDING_PAYMENT);
        order.setCustomerId(currentUser.subject().orElse(null));
        order.setCustomerEmail(req.buyer().email());
        order.setCustomerName(req.buyer().name());
        order.setCurrency(event.getCurrency());
        order.setSubtotalMinor(subtotal);
        order.setDiscountMinor(0);
        order.setTaxMinor(0);
        order.setTotalMinor(subtotal); // subtotal - discount + tax; both 0 in MVP
        order.setProvider(event.getPaymentProvider().name());
        order.setReturnUrl(req.returnUrl());
        order.setBuyerFields(toJsonOrNull(buyerValues));

        if (free) {
            order.setPaidAt(Instant.now());
        } else {
            order.setExpiresAt(Instant.now().plus(RESERVATION_TTL));
        }
        Order saved = orders.save(order);

        // Persist attendees (now that the order has an id).
        for (PendingAttendee pending : pendingAttendees) {
            Attendee attendee = new Attendee();
            attendee.setOrder(saved);
            attendee.setTicketType(pending.ticketType);
            attendee.setName(pending.input.name());
            attendee.setEmail(pending.input.email());
            attendee.setPhone(pending.input.phone());
            attendee.setFields(toJsonOrNull(pending.cleanedFields));
            attendees.save(attendee);
        }

        if (free) {
            // Free order: no payment step — issue immediately (attendees already saved).
            ticketIssuanceService.issueForOrder(saved);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Order get(UUID id) {
        Order order = orders.findById(id)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + id));
        order.getItems().forEach(item -> item.getTicketType().getName());
        return order;
    }

    // ---- helpers -----------------------------------------------------------

    /** Mutable holder while we validate before persisting attendees. */
    private static final class PendingAttendee {
        private final TicketType ticketType;
        private final CreateOrderRequest.Attendee input;
        private Map<String, String> cleanedFields = Map.of();

        PendingAttendee(TicketType ticketType, CreateOrderRequest.Attendee input) {
            this.ticketType = ticketType;
            this.input = input;
        }
    }

    /** Keeps only recognized keys and enforces required fields. */
    private Map<String, String> cleanAndValidate(List<CustomField> defs, Map<String, String> submitted, String context) {
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

    private String toJsonOrNull(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return objectMapper.writeValueAsString(values);
    }

    private void rejectDuplicateTicketTypes(List<CreateOrderRequest.Line> lines) {
        Set<UUID> seen = new HashSet<>();
        for (CreateOrderRequest.Line line : lines) {
            if (!seen.add(line.ticketTypeId())) {
                throw ApiException.validation(
                        "Duplicate ticket type in order: " + line.ticketTypeId()
                                + " (combine into a single line)");
            }
        }
    }

    private void assertSellable(TicketType tt, int quantity) {
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
}
