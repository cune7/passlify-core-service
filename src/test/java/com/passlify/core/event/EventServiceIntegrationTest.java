package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.payment.PaymentProvider;
import com.passlify.core.organization.OrganizationKind;
import com.passlify.core.organization.OrganizationService;
import com.passlify.core.organization.dto.UpsertOrganizationRequest;
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

/** Exercises ownership stamping, slug generation and the publish guards end-to-end. */
class EventServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventService eventService;

    @Autowired
    TicketTypeService ticketTypeService;

    @Autowired
    OrganizationService organizationService;

    @Autowired
    EventContactService contactService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createStampsOrganizerAndGeneratesSlug() {
        authenticate("organizer-1", "ORGANIZER");

        Event created = eventService.create(sampleEvent("Summer Fest 2026"));

        assertThat(created.getOrganizerId()).isEqualTo("organizer-1");
        assertThat(created.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(created.getSlug()).startsWith("summer-fest-2026-");
    }

    @Test
    void publishRequiresAnActiveTicketType() {
        authenticate("organizer-1", "ORGANIZER");
        Event event = eventService.create(sampleEvent("Needs Tickets"));

        // No ticket types yet → cannot publish.
        assertThatThrownBy(() -> eventService.publish(event.getId()))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_STATE));

        // Add an active PAID ticket type; publishing now also requires a company profile.
        ticketTypeService.create(event.getId(), new CreateTicketTypeRequest(
                "Regular", null, 250_000L, null, 100, null, null, null, null, null, null, null, null));
        makeCompany();
        com.passlify.core.support.EventFixtures.addContact(contactService, event.getId());

        Event published = eventService.publish(event.getId());
        assertThat(published.getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    /** Upgrades the current user's org to a billable COMPANY so paid events can publish. */
    private void makeCompany() {
        organizationService.upsertMine(new UpsertOrganizationRequest(
                OrganizationKind.COMPANY, "Org One d.o.o.", "Org One d.o.o.",
                "123456789", "21234567", "Savska 5", "Belgrade", "11000", "RS", null, null, null));
    }

    @Test
    void anotherOrganizerCannotSeeForeignEvent() {
        authenticate("organizer-1", "ORGANIZER");
        Event event = eventService.create(sampleEvent("Private to org 1"));
        UUID id = event.getId();

        authenticate("organizer-2", "ORGANIZER");
        assertThatThrownBy(() -> eventService.getOwned(id))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    private CreateEventRequest sampleEvent(String name) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new CreateEventRequest(
                name, "desc", null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, CommercialMode.PAID, null, null,
                com.passlify.core.support.EventFixtures.TEST_LOCATION, 500,
                List.of("test"), "RSD", Visibility.PUBLIC, PaymentProvider.MOCK);
    }

    private void authenticate(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(subject)
                .build();
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, authorities);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
