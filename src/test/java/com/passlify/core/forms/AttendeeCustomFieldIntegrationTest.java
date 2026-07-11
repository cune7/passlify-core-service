package com.passlify.core.forms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.event.Event;
import com.passlify.core.event.EventService;
import com.passlify.core.event.Visibility;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.forms.dto.CreateCustomFieldRequest;
import com.passlify.core.issuance.Ticket;
import com.passlify.core.issuance.TicketRepository;
import com.passlify.core.order.Attendee;
import com.passlify.core.order.AttendeeRepository;
import com.passlify.core.order.CheckoutService;
import com.passlify.core.order.Order;
import com.passlify.core.order.dto.CreateOrderRequest;
import com.passlify.core.support.AbstractIntegrationTest;
import com.passlify.core.ticket.AttendeeDataMode;
import com.passlify.core.ticket.TicketType;
import com.passlify.core.ticket.TicketTypeService;
import com.passlify.core.ticket.dto.CreateTicketTypeRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Custom fields + per-attendee data: capture, validation, and ticket linkage. */
class AttendeeCustomFieldIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventService eventService;

    @Autowired
    TicketTypeService ticketTypeService;

    @Autowired
    CustomFieldService customFieldService;

    @Autowired
    CheckoutService checkoutService;

    @Autowired
    TicketRepository tickets;

    @Autowired
    AttendeeRepository attendees;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void collectsBuyerAndAttendeeFieldsAndLinksTicketsToAttendees() {
        TicketType tt = setUp();   // free EACH_TICKET type + a required field of each scope

        SecurityContextHolder.clearContext();   // buyer is a guest
        CreateOrderRequest req = new CreateOrderRequest(
                new CreateOrderRequest.Buyer("buyer@example.com", "Bruno Buyer", Map.of("company", "Acme d.o.o.")),
                List.of(new CreateOrderRequest.Line(tt.getId(), 2, List.of(
                        new CreateOrderRequest.Attendee("Ана Анић", "ana@x.com", null, Map.of("tshirt_size", "M")),
                        new CreateOrderRequest.Attendee("Marko Marić", "marko@x.com", null, Map.of("tshirt_size", "L"))))),
                null);

        Order order = checkoutService.createOrder(req);

        assertThat(order.getBuyerFields()).contains("Acme");

        List<Attendee> saved = attendees.findByOrderId(order.getId());
        assertThat(saved).hasSize(2);
        assertThat(saved).anySatisfy(a -> {
            assertThat(a.getName()).isEqualTo("Ана Анић");
            assertThat(a.getFields()).contains("M");
        });

        // Free order → issued immediately; each ticket carries its attendee's name.
        List<Ticket> issued = tickets.findByOrderIdOrderBySerialNumberAsc(order.getId());
        assertThat(issued).hasSize(2);
        assertThat(issued).extracting(Ticket::getAttendeeName)
                .containsExactlyInAnyOrder("Ана Анић", "Marko Marić");
    }

    @Test
    void rejectsMissingRequiredBuyerField() {
        TicketType tt = setUp();
        SecurityContextHolder.clearContext();
        CreateOrderRequest req = new CreateOrderRequest(
                new CreateOrderRequest.Buyer("buyer@example.com", "Bruno", Map.of()),   // no 'company'
                List.of(new CreateOrderRequest.Line(tt.getId(), 1, List.of(
                        new CreateOrderRequest.Attendee("Ana", null, null, Map.of("tshirt_size", "M"))))),
                null);

        assertThatThrownBy(() -> checkoutService.createOrder(req))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void rejectsWrongAttendeeCount() {
        TicketType tt = setUp();
        SecurityContextHolder.clearContext();
        CreateOrderRequest req = new CreateOrderRequest(
                new CreateOrderRequest.Buyer("buyer@example.com", "Bruno", Map.of("company", "Acme")),
                List.of(new CreateOrderRequest.Line(tt.getId(), 2, List.of(   // 2 tickets, 1 attendee
                        new CreateOrderRequest.Attendee("Ana", null, null, Map.of("tshirt_size", "M"))))),
                null);

        assertThatThrownBy(() -> checkoutService.createOrder(req))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    /** Organizer sets up a published event with a free EACH_TICKET type + two required fields. */
    private TicketType setUp() {
        authenticate("organizer-1", "ORGANIZER");
        Instant start = Instant.now().plus(20, ChronoUnit.DAYS);
        Event event = eventService.create(new CreateEventRequest(
                "Conf 2026", "desc", null, start, start.plus(8, ChronoUnit.HOURS),
                "Europe/Belgrade", null, null, null, null, null, 500, List.of(),
                "RSD", Visibility.PUBLIC, null));

        customFieldService.create(event.getId(), new CreateCustomFieldRequest(
                "company", "Company", FieldType.TEXT, FieldScope.PER_PURCHASE, true, null, 0));
        customFieldService.create(event.getId(), new CreateCustomFieldRequest(
                "tshirt_size", "T-shirt size", FieldType.SELECT, FieldScope.PER_ATTENDEE, true,
                List.of("S", "M", "L", "XL"), 1));

        TicketType tt = ticketTypeService.create(event.getId(), new CreateTicketTypeRequest(
                "Workshop", null, 0L, null, 100, null, null, null, null, null, null,
                AttendeeDataMode.EACH_TICKET, 0));
        eventService.publish(event.getId());
        return tt;
    }

    private void authenticate(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(subject).build();
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
