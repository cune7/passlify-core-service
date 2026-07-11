package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.support.AbstractIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Verifies the V9 migration produced a category→leaf hierarchy and that only leaves are selectable. */
class EventTypeHierarchyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventTypeRepository eventTypes;

    @Autowired
    EventService eventService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void migrationLinksLeavesToNonSelectableCategories() {
        EventType leaf = eventTypes.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .filter(EventType::isSelectable).findFirst().orElseThrow();
        assertThat(leaf.getParent()).isNotNull();
        assertThat(leaf.getParent().isSelectable()).isFalse();
        assertThat(leaf.getParent().getParent()).isNull();
        assertThat(leaf.getCode()).contains(".");
    }

    @Test
    void createWithLeafTypeSucceedsAndExposesParent() {
        authenticate("organizer-1", "ORGANIZER");
        EventType leaf = firstLeaf();

        Event event = eventService.create(eventWithType(leaf));
        assertThat(event.getEventType().getId()).isEqualTo(leaf.getId());
        assertThat(event.getEventType().getParent()).isNotNull();
    }

    @Test
    void createWithNonSelectableCategoryIsRejected() {
        authenticate("organizer-1", "ORGANIZER");
        EventType category = eventTypes.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .filter(t -> !t.isSelectable()).findFirst().orElseThrow();

        assertThatThrownBy(() -> eventService.create(eventWithType(category)))
                .isInstanceOf(ApiException.class);
    }

    private EventType firstLeaf() {
        return eventTypes.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .filter(EventType::isSelectable).findFirst().orElseThrow();
    }

    private CreateEventRequest eventWithType(EventType type) {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new CreateEventRequest(
                "Typed Event", null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, null, type.getId(), null, null, 100, List.of(),
                "RSD", Visibility.PUBLIC, null);
    }

    private void authenticate(String subject, String... roles) {
        Jwt jwt = Jwt.withTokenValue("test-token").header("alg", "none").subject(subject).build();
        var authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, authorities));
    }
}
