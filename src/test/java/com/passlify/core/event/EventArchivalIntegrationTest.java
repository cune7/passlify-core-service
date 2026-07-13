package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.support.AbstractIntegrationTest;
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

class EventArchivalIntegrationTest extends AbstractIntegrationTest {

    @Autowired EventService eventService;
    @Autowired EventRepository events;
    @Autowired PublicCatalogService catalog;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void organizerBoardHidesArchivedButFilterReveals() {
        authenticate("organizer-1", "ORGANIZER");
        UUID kept = eventService.create(draft("Kept Fest")).getId();
        UUID archived = eventService.create(draft("Archived Fest")).getId();

        eventService.archive(archived);

        assertThat(ownedIds(false)).contains(kept).doesNotContain(archived); // default board
        assertThat(ownedIds(true)).contains(kept, archived);                 // reporting/history filter

        eventService.unarchive(archived);
        assertThat(ownedIds(false)).contains(kept, archived);
    }

    @Test
    void publicCatalogHidesArchivedAndOnlyShowsPastWhenAsked() {
        UUID active = persist("arch-active", EventStatus.PUBLISHED, false, future());
        UUID past = persist("arch-past", EventStatus.COMPLETED, false, hoursAgo(48));
        UUID archivedPast = persist("arch-archived", EventStatus.COMPLETED, true, hoursAgo(48));

        List<UUID> upcoming = publicIds(false);
        assertThat(upcoming).contains(active).doesNotContain(past, archivedPast);

        List<UUID> withHistory = publicIds(true);
        assertThat(withHistory).contains(active, past).doesNotContain(archivedPast); // archived never public
    }

    private List<UUID> ownedIds(boolean includeArchived) {
        return eventService.listOwned(null, includeArchived, PageRequest.of(0, 50))
                .getContent().stream().map(Event::getId).toList();
    }

    private List<UUID> publicIds(boolean includePast) {
        return catalog.search(null, null, null, null, null, includePast, PageRequest.of(0, 50))
                .getContent().stream().map(Event::getId).toList();
    }

    private CreateEventRequest draft(String name) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new CreateEventRequest(name, null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, null, null, null, null, 500, List.of(),
                "RSD", Visibility.PUBLIC, null);
    }

    private static Instant future() {
        return Instant.now().plus(30, ChronoUnit.DAYS);
    }

    private static Instant hoursAgo(int h) {
        return Instant.now().minus(h, ChronoUnit.HOURS);
    }

    private UUID persist(String slug, EventStatus status, boolean archived, Instant endsAt) {
        Event e = new Event();
        e.setName("Archival " + slug);
        e.setSlug(slug + "-" + UUID.randomUUID().toString().substring(0, 8));
        e.setStatus(status);
        e.setVisibility(Visibility.PUBLIC);
        e.setArchived(archived);
        e.setCurrency("RSD");
        e.setOrganizerId("organizer-1");
        e.setStartsAt(endsAt.minus(2, ChronoUnit.HOURS));
        e.setEndsAt(endsAt);
        return events.save(e).getId();
    }

    private void authenticate(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(subject).build();
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
