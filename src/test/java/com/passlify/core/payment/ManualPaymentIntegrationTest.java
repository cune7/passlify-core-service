package com.passlify.core.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.CommercialMode;
import com.passlify.core.event.EventService;
import com.passlify.core.event.Visibility;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.order.Order;
import com.passlify.core.order.OrderRepository;
import com.passlify.core.order.OrderStatus;
import com.passlify.core.order.dto.CreateOrderRequest;
import com.passlify.core.order.CheckoutService;
import com.passlify.core.organization.OrganizationKind;
import com.passlify.core.organization.OrganizationService;
import com.passlify.core.organization.dto.UpsertOrganizationRequest;
import com.passlify.core.payment.dto.ManualPaymentInstructions;
import com.passlify.core.payment.dto.PaymentSessionResponse;
import com.passlify.core.support.AbstractIntegrationTest;
import com.passlify.core.ticket.TicketType;
import com.passlify.core.ticket.TicketTypeService;
import com.passlify.core.ticket.dto.CreateTicketTypeRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ManualPaymentIntegrationTest extends AbstractIntegrationTest {

    @Autowired EventService eventService;
    @Autowired TicketTypeService ticketTypeService;
    @Autowired OrganizationService organizationService;
    @Autowired CheckoutService checkoutService;
    @Autowired PaymentService paymentService;
    @Autowired OrderRepository orders;
    @Autowired com.passlify.core.event.EventContactService contactService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void instructionsThenOrganizerConfirmMarksPaid() {
        UUID orderId = setUpManualOrder();

        PaymentSessionResponse session = paymentService.createSession(orderId, null, null);
        assertThat(session.checkoutUrl()).contains("/api/v1/public/payments/manual/instructions/" + orderId);

        ManualPaymentInstructions info = paymentService.manualInstructions(orderId);
        assertThat(info.accountNumber()).isEqualTo("265-1234567890123-45");
        assertThat(info.accountHolder()).isEqualTo("Org One d.o.o.");
        assertThat(info.reference()).isEqualTo(orderId.toString());

        // Organizer reconciles the transfer and confirms.
        paymentService.confirmManualPayment(orderId);
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
        // Idempotent.
        paymentService.confirmManualPayment(orderId);
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void rejectCancelsAndReleasesInventory() {
        UUID orderId = setUpManualOrder();
        paymentService.rejectManualPayment(orderId);
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void nonManagerCannotConfirm() {
        UUID orderId = setUpManualOrder();
        authenticate("stranger", "ORGANIZER");
        assertThatThrownBy(() -> paymentService.confirmManualPayment(orderId))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void fullRefundVoidsAndMarksRefunded() {
        UUID orderId = setUpManualOrder();
        paymentService.confirmManualPayment(orderId); // → PAID
        paymentService.refund(orderId, null);          // full
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void partialRefundMarksPartiallyRefunded() {
        UUID orderId = setUpManualOrder();
        paymentService.confirmManualPayment(orderId); // → PAID (total 500_000)
        paymentService.refund(orderId, 200_000L);
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.PARTIALLY_REFUNDED);
        // A follow-up full refund of the remainder completes it.
        paymentService.refund(orderId, null);
        assertThat(orders.findById(orderId).orElseThrow().getStatus()).isEqualTo(OrderStatus.REFUNDED);
    }

    @Test
    void cannotRefundAnUnpaidOrder() {
        UUID orderId = setUpManualOrder(); // still PENDING_PAYMENT
        assertThatThrownBy(() -> paymentService.refund(orderId, null)).isInstanceOf(ApiException.class);
    }

    /** Creates a published MANUAL paid event (COMPANY w/ bank details) + a PENDING order. */
    private UUID setUpManualOrder() {
        authenticate("organizer-1", "ORGANIZER");
        organizationService.upsertMine(new UpsertOrganizationRequest(
                OrganizationKind.COMPANY, "Org One d.o.o.", "Org One d.o.o.",
                "123456789", "21234567", "Savska 5", "Belgrade", "11000", "RS", null,
                "265-1234567890123-45", "Org One d.o.o."));
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        UUID eventId = eventService.create(new CreateEventRequest(
                "Manual Fest", null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, CommercialMode.PAID, null, null,
                com.passlify.core.support.EventFixtures.TEST_LOCATION, 500, List.of(),
                "RSD", Visibility.PUBLIC, PaymentProvider.MANUAL)).getId();
        TicketType tt = ticketTypeService.create(eventId, new CreateTicketTypeRequest(
                "Regular", null, 250_000L, null, 100, null, null, null, null, null, null, null, null));
        com.passlify.core.support.EventFixtures.addContact(contactService, eventId);
        eventService.publish(eventId);

        Order order = checkoutService.createOrder(new CreateOrderRequest(
                new CreateOrderRequest.Buyer("buyer@example.com", "Buyer", null),
                List.of(new CreateOrderRequest.Line(tt.getId(), 2, null)), null));
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        return order.getId();
    }

    private void authenticate(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject).build();
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
