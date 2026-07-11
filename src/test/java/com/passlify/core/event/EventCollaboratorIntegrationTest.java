package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.dto.CollaboratorResponse;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.event.dto.InviteCollaboratorRequest;
import com.passlify.core.event.dto.UpdateCollaboratorRoleRequest;
import com.passlify.core.support.AbstractIntegrationTest;
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

class EventCollaboratorIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventService eventService;

    @Autowired
    EventCollaboratorService collaborators;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void ownerRowIsCreatedOnEventCreation() {
        authenticate("organizer-1", "owner@x.rs", "ORGANIZER");
        UUID eventId = eventService.create(event()).getId();

        List<CollaboratorResponse> list = collaborators.list(eventId);
        assertThat(list).singleElement().satisfies(c -> {
            assertThat(c.role()).isEqualTo(EventRole.OWNER);
            assertThat(c.invitationStatus()).isEqualTo(InvitationStatus.ACCEPTED);
            assertThat(c.userId()).isEqualTo("organizer-1");
        });
    }

    @Test
    void inviteThenAcceptLinksTheInviteeSubject() {
        authenticate("organizer-1", "owner@x.rs", "ORGANIZER");
        UUID eventId = eventService.create(event()).getId();

        CollaboratorResponse invited = collaborators.invite(eventId,
                new InviteCollaboratorRequest("friend@x.rs", EventRole.EDITOR));
        assertThat(invited.invitationStatus()).isEqualTo(InvitationStatus.PENDING);
        assertThat(invited.userId()).isNull();
        assertThat(collaborators.list(eventId)).hasSize(2);

        // The invitee accepts (email claim must match the invite).
        authenticate("user-2", "friend@x.rs", "ORGANIZER");
        CollaboratorResponse accepted = collaborators.accept(eventId, invited.id());
        assertThat(accepted.invitationStatus()).isEqualTo(InvitationStatus.ACCEPTED);
        assertThat(accepted.userId()).isEqualTo("user-2");
    }

    @Test
    void acceptByAWrongEmailIsForbidden() {
        authenticate("organizer-1", "owner@x.rs", "ORGANIZER");
        UUID eventId = eventService.create(event()).getId();
        CollaboratorResponse invited = collaborators.invite(eventId,
                new InviteCollaboratorRequest("friend@x.rs", EventRole.VIEWER));

        authenticate("intruder", "someone-else@x.rs", "ORGANIZER");
        assertThatThrownBy(() -> collaborators.accept(eventId, invited.id()))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void nonManagerCannotInvite_andOwnerRoleCannotBeInvited() {
        authenticate("organizer-1", "owner@x.rs", "ORGANIZER");
        UUID eventId = eventService.create(event()).getId();

        assertThatThrownBy(() -> collaborators.invite(eventId,
                new InviteCollaboratorRequest("x@x.rs", EventRole.OWNER)))
                .isInstanceOf(ApiException.class);

        authenticate("stranger", "stranger@x.rs", "ORGANIZER");
        assertThatThrownBy(() -> collaborators.invite(eventId,
                new InviteCollaboratorRequest("y@x.rs", EventRole.EDITOR)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void changeRoleAndRemove() {
        authenticate("organizer-1", "owner@x.rs", "ORGANIZER");
        UUID eventId = eventService.create(event()).getId();
        CollaboratorResponse invited = collaborators.invite(eventId,
                new InviteCollaboratorRequest("friend@x.rs", EventRole.EDITOR));

        CollaboratorResponse reroled = collaborators.changeRole(eventId, invited.id(),
                new UpdateCollaboratorRoleRequest(EventRole.VIEWER));
        assertThat(reroled.role()).isEqualTo(EventRole.VIEWER);

        collaborators.remove(eventId, invited.id());
        assertThat(collaborators.list(eventId)).hasSize(1); // owner only
    }

    private CreateEventRequest event() {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new CreateEventRequest(
                "Collab Fest", null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, null, null, null, null, 100, List.of(),
                "RSD", Visibility.PUBLIC, null);
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
