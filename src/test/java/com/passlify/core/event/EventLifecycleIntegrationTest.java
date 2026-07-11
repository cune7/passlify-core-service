package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.event.dto.EventAuditResponse;
import com.passlify.core.event.dto.PublicationReadinessResponse;
import com.passlify.core.organization.OrganizationKind;
import com.passlify.core.organization.OrganizationService;
import com.passlify.core.organization.dto.UpsertOrganizationRequest;
import com.passlify.core.payment.PaymentProvider;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class EventLifecycleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventService eventService;

    @Autowired
    TicketTypeService ticketTypeService;

    @Autowired
    OrganizationService organizationService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readinessReportsMissingRequirementsForABareEvent() {
        authenticate("organizer-1", "ORGANIZER");
        UUID eventId = eventService.create(freeEvent("Bare Event")).getId();

        PublicationReadinessResponse r = eventService.readiness(eventId);
        assertThat(r.ready()).isFalse();
        assertThat(r.violations()).extracting(PublicationReadinessResponse.Violation::code)
                .contains("TICKET_TYPE_REQUIRED", "CONTACT_REQUIRED", "LOCATION_REQUIRED", "EVENT_TYPE_REQUIRED");
    }

    @Test
    void createRecordsAudit() {
        authenticate("organizer-1", "ORGANIZER");
        UUID eventId = eventService.create(freeEvent("Audited")).getId();

        assertThat(eventService.listAudit(eventId, PageRequest.of(0, 20)).getContent())
                .extracting(EventAuditResponse::action)
                .containsExactly(EventAuditAction.EVENT_CREATED);
    }

    @Test
    void publishThenCompleteTransitionsAndAudits() {
        authenticate("organizer-1", "ORGANIZER");
        UUID eventId = eventService.create(paidEvent("Lifecycle Fest")).getId();
        ticketTypeService.create(eventId, new CreateTicketTypeRequest(
                "Regular", null, 250_000L, null, 100, null, null, null, null, null, null, null, null));
        organizationService.upsertMine(new UpsertOrganizationRequest(
                OrganizationKind.COMPANY, "Org One d.o.o.", "Org One d.o.o.",
                "123456789", "21234567", "Savska 5", "Belgrade", "11000", "RS", null, null, null));

        assertThat(eventService.publish(eventId).getStatus()).isEqualTo(EventStatus.PUBLISHED);
        assertThat(eventService.complete(eventId).getStatus()).isEqualTo(EventStatus.COMPLETED);

        assertThat(eventService.listAudit(eventId, PageRequest.of(0, 20)).getContent())
                .extracting(EventAuditResponse::action)
                .containsExactly(EventAuditAction.EVENT_COMPLETED,
                        EventAuditAction.EVENT_PUBLISHED,
                        EventAuditAction.EVENT_CREATED);
    }

    @Test
    void cancelRecordsReasonAndBlocksComplete() {
        authenticate("organizer-1", "ORGANIZER");
        UUID eventId = eventService.create(freeEvent("To Cancel")).getId();

        assertThat(eventService.cancel(eventId, "Venue unavailable").getStatus())
                .isEqualTo(EventStatus.CANCELLED);
        assertThat(eventService.listAudit(eventId, PageRequest.of(0, 20)).getContent().get(0))
                .satisfies(a -> {
                    assertThat(a.action()).isEqualTo(EventAuditAction.EVENT_CANCELLED);
                    assertThat(a.reason()).isEqualTo("Venue unavailable");
                });
        // A cancelled event cannot be completed.
        assertThatThrownBy(() -> eventService.complete(eventId)).isInstanceOf(ApiException.class);
    }

    private CreateEventRequest freeEvent(String name) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new CreateEventRequest(name, null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, null, null, null, null, 500, List.of(),
                "RSD", Visibility.PUBLIC, null);
    }

    private CreateEventRequest paidEvent(String name) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new CreateEventRequest(name, null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, CommercialMode.PAID, null, null, null, 500, List.of(),
                "RSD", Visibility.PUBLIC, PaymentProvider.MOCK);
    }

    private void authenticate(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(subject).build();
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
