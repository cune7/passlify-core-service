package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.event.dto.EventContactRequest;
import com.passlify.core.event.dto.EventContactResponse;
import com.passlify.core.support.AbstractIntegrationTest;
import com.passlify.core.support.EventFixtures;
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

/**
 * The contact sub-resource (§18) plus the publish preconditions it and the location
 * feed (§14/§18.2): an event cannot go live without a contact method and, for
 * IN_PERSON/HYBRID, a physical location.
 */
class EventContactIntegrationTest extends AbstractIntegrationTest {

    @Autowired EventService eventService;
    @Autowired EventContactService contactService;
    @Autowired TicketTypeService ticketTypeService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void putReplacesContactAndGetReadsItBack() {
        authenticate("organizer-1");
        UUID eventId = eventService.create(freeEvent(EventFixtures.TEST_LOCATION)).getId();

        EventContactResponse saved = contactService.update(eventId, new EventContactRequest(
                "hello@fest.rs", "+381600000000", "https://fest.rs",
                "https://facebook.com/fest", null, null, null, null, null,
                true, false, true, true));

        assertThat(saved.email()).isEqualTo("hello@fest.rs");
        assertThat(saved.websiteUrl()).isEqualTo("https://fest.rs");
        assertThat(saved.showEmail()).isTrue();
        assertThat(contactService.get(eventId).phone()).isEqualTo("+381600000000");
    }

    @Test
    void rejectsANonHttpWebsiteUrl() {
        authenticate("organizer-1");
        UUID eventId = eventService.create(freeEvent(EventFixtures.TEST_LOCATION)).getId();

        assertThatThrownBy(() -> contactService.update(eventId, new EventContactRequest(
                null, null, "ftp://fest.rs", null, null, null, null, null, null,
                null, null, null, null)))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void publishIsBlockedWithoutAContactMethod() {
        authenticate("organizer-1");
        UUID eventId = eventService.create(freeEvent(EventFixtures.TEST_LOCATION)).getId();
        freeTicket(eventId);

        // Has a location but no contact → refused.
        assertThatThrownBy(() -> eventService.publish(eventId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_STATE));

        EventFixtures.addContact(contactService, eventId);
        assertThat(eventService.publish(eventId).getStatus()).isEqualTo(EventStatus.PUBLISHED);
    }

    @Test
    void publishIsBlockedWithoutAPhysicalLocationForInPersonEvents() {
        authenticate("organizer-1");
        UUID eventId = eventService.create(freeEvent(null)).getId();  // no location
        freeTicket(eventId);
        EventFixtures.addContact(contactService, eventId);

        assertThatThrownBy(() -> eventService.publish(eventId))
                .isInstanceOfSatisfying(ApiException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ErrorCode.INVALID_STATE));
    }

    private CreateEventRequest freeEvent(com.passlify.core.event.dto.LocationDto location) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new CreateEventRequest("Contact Fest", null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, null, null, null, location, 500, List.of(),
                "RSD", Visibility.PUBLIC, null);
    }

    private void freeTicket(UUID eventId) {
        ticketTypeService.create(eventId, new CreateTicketTypeRequest(
                "Free", null, 0L, null, 100, null, null, null, null, null, null, null, null));
    }

    private void authenticate(String subject) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))));
    }
}
