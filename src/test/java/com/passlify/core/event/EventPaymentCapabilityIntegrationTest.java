package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.event.dto.PublicationReadinessResponse.Violation;
import com.passlify.core.organization.OrganizationKind;
import com.passlify.core.organization.OrganizationService;
import com.passlify.core.organization.dto.UpsertOrganizationRequest;
import com.passlify.core.payment.PaymentCapabilityService;
import com.passlify.core.payment.PaymentProvider;
import com.passlify.core.payment.dto.GrantCapabilityRequest;
import com.passlify.core.support.AbstractIntegrationTest;
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

/** A paid STRIPE event may only publish once an admin grants a matching capability (§10). */
class EventPaymentCapabilityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventService eventService;

    @Autowired
    TicketTypeService ticketTypeService;

    @Autowired
    OrganizationService organizationService;

    @Autowired
    PaymentCapabilityService capabilities;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void stripeEventBlockedUntilCapabilityGrantedThenPublishes() {
        authenticate("organizer-1", "ORGANIZER");
        UUID eventId = eventService.create(stripeEvent("RSD")).getId();
        makeCompany();
        pricedTicket(eventId);
        UUID orgId = eventService.getOwned(eventId).getOrganizationId();

        // No capability yet → readiness flags it and publish is refused.
        assertThat(codes(eventId)).contains("PAYMENT_PROVIDER_NOT_APPROVED");
        assertThatThrownBy(() -> eventService.publish(eventId)).isInstanceOf(ApiException.class);

        capabilities.grant(orgId, new GrantCapabilityRequest(
                PaymentProvider.STRIPE, List.of("RSD"), null, null, "acct_test"));

        assertThat(codes(eventId)).doesNotContain("PAYMENT_PROVIDER_NOT_APPROVED", "CURRENCY_NOT_SUPPORTED");
        assertThat(eventService.publish(eventId).getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void currencyOutsideTheCapabilityIsRejected() {
        authenticate("organizer-1", "ORGANIZER");
        UUID eventId = eventService.create(stripeEvent("RSD")).getId();
        makeCompany();
        pricedTicket(eventId);
        UUID orgId = eventService.getOwned(eventId).getOrganizationId();

        // Capability exists but only for EUR — the RSD event stays blocked.
        capabilities.grant(orgId, new GrantCapabilityRequest(
                PaymentProvider.STRIPE, List.of("EUR"), null, null, "acct_test"));

        assertThat(codes(eventId)).contains("CURRENCY_NOT_SUPPORTED");
        assertThatThrownBy(() -> eventService.publish(eventId)).isInstanceOf(ApiException.class);
    }

    private List<String> codes(UUID eventId) {
        return eventService.readiness(eventId).violations().stream().map(Violation::code).toList();
    }

    private void makeCompany() {
        organizationService.upsertMine(new UpsertOrganizationRequest(
                OrganizationKind.COMPANY, "Org One d.o.o.", "Org One d.o.o.",
                "123456789", "21234567", "Savska 5", "Belgrade", "11000", "RS", null));
    }

    private void pricedTicket(UUID eventId) {
        ticketTypeService.create(eventId, new CreateTicketTypeRequest(
                "Regular", null, 250_000L, null, 100, null, null, null, null, null, null, null, null));
    }

    private CreateEventRequest stripeEvent(String currency) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new CreateEventRequest("Stripe Fest", null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, CommercialMode.PAID, null, null, null, 500, List.of(),
                currency, Visibility.PUBLIC, PaymentProvider.STRIPE);
    }

    private void authenticate(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(subject).build();
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
