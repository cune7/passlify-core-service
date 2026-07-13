package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.support.AbstractIntegrationTest;
import com.passlify.core.ticket.TicketTypeService;
import com.passlify.core.ticket.dto.CreateTicketTypeRequest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Public reachability by visibility (§8, §25.3): UNLISTED works by slug but is not listed; PRIVATE 404s. */
class EventVisibilityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventService eventService;

    @Autowired
    TicketTypeService ticketTypeService;

    @Autowired
    PublicCatalogService catalog;

    @Autowired
    EventContactService contactService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unlistedReachableBySlugButNotListed_privateIs404() {
        authenticate("organizer-1", "ORGANIZER");
        Event unlisted = publish(Visibility.UNLISTED);
        Event pub = publish(Visibility.PUBLIC);
        Event priv = publish(Visibility.PRIVATE);

        // Direct-link resolution: PUBLIC and UNLISTED resolve; PRIVATE does not.
        assertThat(catalog.getPublishedBySlug(unlisted.getSlug()).getId()).isEqualTo(unlisted.getId());
        assertThat(catalog.getPublishedBySlug(pub.getSlug()).getId()).isEqualTo(pub.getId());
        assertThatThrownBy(() -> catalog.getPublishedBySlug(priv.getSlug()))
                .isInstanceOf(ApiException.class);

        // Listing shows only PUBLIC.
        List<Event> listed = catalog.search(null, null, null, null, null, false, PageRequest.of(0, 50)).getContent();
        assertThat(listed).extracting(Event::getId).contains(pub.getId())
                .doesNotContain(unlisted.getId(), priv.getId());
    }

    private Event publish(Visibility visibility) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        Event e = eventService.create(new CreateEventRequest(
                "Vis " + visibility, null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, null, null, null,
                com.passlify.core.support.EventFixtures.TEST_LOCATION, 500, List.of(),
                "RSD", visibility, null));
        ticketTypeService.create(e.getId(), new CreateTicketTypeRequest(
                "Free", null, 0L, null, 100, null, null, null, null, null, null, null, null));
        com.passlify.core.support.EventFixtures.addContact(contactService, e.getId());
        return eventService.publish(e.getId());
    }

    private void authenticate(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(subject).build();
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
