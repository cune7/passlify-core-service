package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.event.dto.EventAuditResponse;
import com.passlify.core.event.dto.UpdateEventRequest;
import com.passlify.core.issuance.TicketRepository;
import com.passlify.core.order.CheckoutService;
import com.passlify.core.order.dto.CreateOrderRequest;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class EventScheduleChangeIntegrationTest extends AbstractIntegrationTest {

    @Autowired EventService eventService;
    @Autowired TicketTypeService ticketTypeService;
    @Autowired CheckoutService checkoutService;
    @Autowired TicketRepository tickets;
    @Autowired EventContactService contactService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reschedulingAPublishedEventAuditsAndReachesHolders() {
        authenticate("organizer-1", "ORGANIZER");
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        UUID eventId = eventService.create(freeEvent("Reschedule Fest", start)).getId();
        TicketType tt = ticketTypeService.create(eventId, freeTicket());
        com.passlify.core.support.EventFixtures.addContact(contactService, eventId);
        eventService.publish(eventId);

        // A holder exists (free order issues tickets at checkout).
        checkoutService.createOrder(new CreateOrderRequest(
                new CreateOrderRequest.Buyer("buyer@example.com", "Buyer", null),
                List.of(new CreateOrderRequest.Line(tt.getId(), 1, null)), null));
        assertThat(tickets.findHolderEmailsByEventId(eventId)).contains("buyer@example.com");

        // Move the date → SCHEDULE_CHANGED audit + notification event fired (mail is best-effort).
        Instant moved = start.plus(7, ChronoUnit.DAYS);
        eventService.update(eventId, dateUpdate(moved, moved.plus(4, ChronoUnit.HOURS)));

        assertThat(auditActions(eventId)).contains(EventAuditAction.SCHEDULE_CHANGED);
    }

    @Test
    void reschedulingADraftDoesNotEmitScheduleChange() {
        authenticate("organizer-1", "ORGANIZER");
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        UUID eventId = eventService.create(freeEvent("Draft Fest", start)).getId();

        Instant moved = start.plus(3, ChronoUnit.DAYS);
        eventService.update(eventId, dateUpdate(moved, moved.plus(4, ChronoUnit.HOURS)));

        assertThat(auditActions(eventId))
                .contains(EventAuditAction.EVENT_UPDATED)
                .doesNotContain(EventAuditAction.SCHEDULE_CHANGED);
    }

    private List<EventAuditAction> auditActions(UUID eventId) {
        return eventService.listAudit(eventId, PageRequest.of(0, 50)).getContent()
                .stream().map(EventAuditResponse::action).toList();
    }

    private CreateEventRequest freeEvent(String name, Instant start) {
        return new CreateEventRequest(name, null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, null, null, null,
                com.passlify.core.support.EventFixtures.TEST_LOCATION, 500, List.of(),
                "RSD", Visibility.PUBLIC, null);
    }

    private CreateTicketTypeRequest freeTicket() {
        return new CreateTicketTypeRequest("Free", null, 0L, null, 100,
                null, null, null, null, null, null, null, null);
    }

    private UpdateEventRequest dateUpdate(Instant startsAt, Instant endsAt) {
        return new UpdateEventRequest(null, null, null, null, startsAt, endsAt, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private void authenticate(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject).build();
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
