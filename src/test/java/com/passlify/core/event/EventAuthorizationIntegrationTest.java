package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.dto.CollaboratorResponse;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.event.dto.InviteCollaboratorRequest;
import com.passlify.core.event.dto.UpdateEventRequest;
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

/** Exercises the collaborator permission matrix (§13.2) across event operations. */
class EventAuthorizationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventService eventService;

    @Autowired
    EventCollaboratorService collaborators;

    @Autowired
    TicketTypeService ticketTypeService;

    @Autowired
    EventContactService contactService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void editorCanEditDetailsButNotCommercialNorLifecycle() {
        authenticate("owner-1", "owner@x.rs", "ORGANIZER");
        UUID eventId = eventService.create(event()).getId();
        inviteAndAccept(eventId, "editor@x.rs", "user-editor", EventRole.EDITOR);

        authenticate("user-editor", "editor@x.rs", "ORGANIZER");
        assertThat(eventService.update(eventId, nameUpdate("Renamed by editor")).getName())
                .isEqualTo("Renamed by editor");
        assertThatThrownBy(() -> eventService.update(eventId, currencyUpdate("EUR")))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> eventService.publish(eventId)).isInstanceOf(ApiException.class);
    }

    @Test
    void viewerCanViewButNotEdit() {
        authenticate("owner-1", "owner@x.rs", "ORGANIZER");
        UUID eventId = eventService.create(event()).getId();
        inviteAndAccept(eventId, "viewer@x.rs", "user-viewer", EventRole.VIEWER);

        authenticate("user-viewer", "viewer@x.rs", "ORGANIZER");
        assertThat(eventService.getOwned(eventId).getId()).isEqualTo(eventId); // VIEW ok
        assertThatThrownBy(() -> eventService.update(eventId, nameUpdate("Nope")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void managerCanPublish() {
        authenticate("owner-1", "owner@x.rs", "ORGANIZER");
        UUID eventId = eventService.create(event()).getId();
        ticketTypeService.create(eventId, new CreateTicketTypeRequest(
                "Free", null, 0L, null, 100, null, null, null, null, null, null, null, null));
        com.passlify.core.support.EventFixtures.addContact(contactService, eventId);
        inviteAndAccept(eventId, "manager@x.rs", "user-manager", EventRole.MANAGER);

        authenticate("user-manager", "manager@x.rs", "ORGANIZER");
        assertThat(eventService.publish(eventId).getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void nonParticipantGets404NotForbidden() {
        authenticate("owner-1", "owner@x.rs", "ORGANIZER");
        UUID eventId = eventService.create(event()).getId();

        authenticate("outsider", "out@x.rs", "ORGANIZER");
        assertThatThrownBy(() -> eventService.getOwned(eventId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode().name()).isEqualTo("NOT_FOUND"));
    }

    // ---- helpers -----------------------------------------------------------

    private void inviteAndAccept(UUID eventId, String email, String subject, EventRole role) {
        // Caller is the owner here.
        CollaboratorResponse invited = collaborators.invite(eventId, new InviteCollaboratorRequest(email, role));
        authenticate(subject, email, "ORGANIZER");
        collaborators.accept(eventId, invited.acceptToken());
        authenticate("owner-1", "owner@x.rs", "ORGANIZER");
    }

    private CreateEventRequest event() {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new CreateEventRequest("Authz Fest", null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, null, null, null,
                com.passlify.core.support.EventFixtures.TEST_LOCATION, 500, List.of(),
                "RSD", Visibility.PUBLIC, null);
    }

    private UpdateEventRequest nameUpdate(String name) {
        return new UpdateEventRequest(name, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private UpdateEventRequest currencyUpdate(String currency) {
        return new UpdateEventRequest(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, currency, null, null, null);
    }

    private void authenticate(String subject, String email, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none")
                .subject(subject).claim("email", email).build();
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
