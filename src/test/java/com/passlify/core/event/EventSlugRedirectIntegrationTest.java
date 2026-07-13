package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.passlify.core.event.dto.CreateEventRequest;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class EventSlugRedirectIntegrationTest extends AbstractIntegrationTest {

    @Autowired EventService eventService;
    @Autowired TicketTypeService ticketTypeService;
    @Autowired PublicCatalogService catalog;
    @Autowired EventContactService contactService;
    @Autowired MockMvc mvc;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void renamingAPublishedSlugKeepsOldLinksWorkingViaRedirect() throws Exception {
        authenticate("organizer-1", "ORGANIZER");
        UUID eventId = publishFreeEvent();
        String oldSlug = eventService.getOwned(eventId).getSlug();

        eventService.update(eventId, slugUpdate("renamed-fest"));
        String newSlug = eventService.getOwned(eventId).getSlug();
        assertThat(newSlug).isEqualTo("renamed-fest");

        // Service view: old slug no longer resolves directly, but redirects to the new one.
        assertThat(catalog.findPublishedBySlug(oldSlug)).isEmpty();
        assertThat(catalog.findPublishedBySlug(newSlug)).isPresent();
        assertThat(catalog.findRedirectTargetSlug(oldSlug)).contains(newSlug);

        // HTTP view: old slug → 301 to the new slug; new slug → 200.
        mvc.perform(get("/api/v1/public/events/{slug}", oldSlug))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(301))
                .andExpect(r -> assertThat(r.getResponse().getHeader("Location"))
                        .isEqualTo("/api/v1/public/events/" + newSlug));
        mvc.perform(get("/api/v1/public/events/{slug}", newSlug))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(200));
    }

    @Test
    void unknownSlugStill404s() throws Exception {
        mvc.perform(get("/api/v1/public/events/{slug}", "no-such-slug"))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isEqualTo(404));
    }

    private UUID publishFreeEvent() {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        UUID eventId = eventService.create(new CreateEventRequest(
                "Slug Fest", null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, null, null, null,
                com.passlify.core.support.EventFixtures.TEST_LOCATION, 500, List.of(),
                "RSD", Visibility.PUBLIC, null)).getId();
        ticketTypeService.create(eventId, new CreateTicketTypeRequest(
                "Free", null, 0L, null, 100, null, null, null, null, null, null, null, null));
        com.passlify.core.support.EventFixtures.addContact(contactService, eventId);
        eventService.publish(eventId);
        return eventId;
    }

    private UpdateEventRequest slugUpdate(String slug) {
        return new UpdateEventRequest(null, slug, null, null, null, null, null, null, null,
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
