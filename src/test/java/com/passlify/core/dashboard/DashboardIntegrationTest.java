package com.passlify.core.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.passlify.core.dashboard.dto.OrderSummary;
import com.passlify.core.dashboard.dto.SalesSummaryResponse;
import com.passlify.core.event.CommercialMode;
import com.passlify.core.event.Event;
import com.passlify.core.event.EventService;
import com.passlify.core.event.Visibility;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.payment.PaymentProvider;
import com.passlify.core.order.CheckoutService;
import com.passlify.core.order.Order;
import com.passlify.core.order.OrderStatus;
import com.passlify.core.order.dto.CreateOrderRequest;
import com.passlify.core.organization.OrganizationKind;
import com.passlify.core.organization.OrganizationService;
import com.passlify.core.organization.dto.UpsertOrganizationRequest;
import com.passlify.core.payment.PaymentService;
import com.passlify.core.payment.dto.PaymentSessionResponse;
import com.passlify.core.support.AbstractIntegrationTest;
import com.passlify.core.ticket.TicketType;
import com.passlify.core.ticket.TicketTypeService;
import com.passlify.core.ticket.dto.CreateTicketTypeRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Organizer dashboard: read models reflect a real paid order, scoped to the owner. */
class DashboardIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventService eventService;

    @Autowired
    TicketTypeService ticketTypeService;

    @Autowired
    CheckoutService checkoutService;

    @Autowired
    PaymentService paymentService;

    @Autowired
    DashboardService dashboardService;

    @Autowired
    OrganizationService organizationService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dashboardReflectsAPaidOrder() {
        authenticate("organizer-1", "ORGANIZER");
        Event event = eventService.create(eventRequest());
        TicketType tt = ticketTypeService.create(event.getId(), ticketTypeRequest());
        // Paid event → organizer must be a billable company before publishing.
        organizationService.upsertMine(new UpsertOrganizationRequest(
                OrganizationKind.COMPANY, "Org One d.o.o.", "Org One d.o.o.",
                "123456789", "21234567", "Savska 5", "Belgrade", "11000", "RS", null));
        eventService.publish(event.getId());

        Order order = checkoutService.createOrder(orderFor(tt));
        PaymentSessionResponse session = paymentService.createSession(order.getId(), null, null);
        paymentService.handleWebhook("mock",
                "{\"type\":\"PAID\",\"sessionId\":\"" + session.sessionId() + "\"}", null);

        SalesSummaryResponse summary = dashboardService.salesSummary(event.getId());
        assertThat(summary.currency()).isEqualTo("RSD");
        assertThat(summary.ticketsIssued()).isEqualTo(2);
        assertThat(summary.ticketsCheckedIn()).isZero();
        assertThat(summary.paidOrders()).isEqualTo(1);
        assertThat(summary.grossRevenueMinor()).isEqualTo(400_000L);
        assertThat(summary.ticketTypes()).singleElement()
                .satisfies(t -> assertThat(t.soldQuantity()).isEqualTo(2));

        Page<OrderSummary> orders = dashboardService.listOrders(event.getId(), PageRequest.of(0, 20));
        assertThat(orders.getTotalElements()).isEqualTo(1);
        assertThat(orders.getContent().get(0).status()).isEqualTo(OrderStatus.PAID);
        assertThat(orders.getContent().get(0).ticketCount()).isEqualTo(2);

        assertThat(dashboardService.listAttendees(event.getId(), PageRequest.of(0, 50)).getTotalElements())
                .isEqualTo(2);

        String csv = dashboardService.attendeesCsv(event.getId());
        assertThat(csv).startsWith("serial_number,ticket_type,attendee_name,email,status,checked_in_at,order_id");
        assertThat(csv.lines().count()).isEqualTo(3);   // header + 2 attendees
    }

    @Test
    void anotherOrganizerCannotSeeForeignDashboard() {
        authenticate("organizer-1", "ORGANIZER");
        Event event = eventService.create(eventRequest());

        authenticate("organizer-2", "ORGANIZER");
        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> dashboardService.salesSummary(event.getId()))
                .isInstanceOf(com.passlify.core.common.error.ApiException.class);
    }

    // ---- fixtures ----------------------------------------------------------

    private CreateEventRequest eventRequest() {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new CreateEventRequest(
                "Dash Fest", "desc", null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, CommercialMode.PAID, null, null, null, 500,
                List.of(), "RSD", Visibility.PUBLIC, PaymentProvider.MOCK);
    }

    private CreateTicketTypeRequest ticketTypeRequest() {
        return new CreateTicketTypeRequest(
                "Regular", null, 200_000L, null, 50, null, null, null, null, null, null, null, null);
    }

    private CreateOrderRequest orderFor(TicketType tt) {
        return new CreateOrderRequest(
                new CreateOrderRequest.Buyer("buyer@example.com", "Buyer", null),
                List.of(new CreateOrderRequest.Line(tt.getId(), 2, null)),
                null);
    }

    private void authenticate(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(subject).build();
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
