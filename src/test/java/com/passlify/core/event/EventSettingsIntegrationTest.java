package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.event.dto.CreateEventRequest;
import com.passlify.core.event.dto.EventSettingsRequest;
import com.passlify.core.event.dto.EventSettingsResponse;
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

class EventSettingsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventService eventService;

    @Autowired
    EventSettingsService settingsService;

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void defaultsBeforeAnySettingsAreSaved() {
        authenticate("organizer-1", "ORGANIZER");
        UUID eventId = eventService.create(freeEvent()).getId();

        EventSettingsResponse defaults = settingsService.get(eventId);
        assertThat(defaults.minimumAge()).isNull();
        assertThat(defaults.multipleEntryAllowed()).isFalse();
        assertThat(defaults.visitorCountryRestrictionEnabled()).isFalse();
    }

    @Test
    void updatePersistsAndNormalizesCountryCodesAndSanitizesRules() {
        authenticate("organizer-1", "ORGANIZER");
        UUID eventId = eventService.create(freeEvent()).getId();

        EventSettingsResponse saved = settingsService.update(eventId, new EventSettingsRequest(
                18, true, true, List.of("rs", "rs", "de"), true, false, 7,
                "<p>Be nice</p><script>alert(1)</script>", "<p>No pets</p>"));

        assertThat(saved.minimumAge()).isEqualTo(18);
        assertThat(saved.ticketsAvailableAtEntrance()).isTrue();
        assertThat(saved.multipleEntryAllowed()).isTrue();
        assertThat(saved.childrenFreeEntryAge()).isEqualTo(7);
        assertThat(saved.allowedVisitorCountryCodes()).containsExactly("RS", "DE");
        assertThat(saved.termsHtml()).contains("Be nice").doesNotContainIgnoringCase("script");

        // Re-read reflects the persisted row.
        assertThat(settingsService.get(eventId).minimumAge()).isEqualTo(18);
    }

    @Test
    void countryRestrictionWithoutCountriesIsRejected() {
        authenticate("organizer-1", "ORGANIZER");
        UUID eventId = eventService.create(freeEvent()).getId();

        assertThatThrownBy(() -> settingsService.update(eventId, new EventSettingsRequest(
                null, false, true, List.of(), false, false, null, null, null)))
                .isInstanceOf(ApiException.class);
    }

    private CreateEventRequest freeEvent() {
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        return new CreateEventRequest(
                "Settings Fest", null, null, start, start.plus(4, ChronoUnit.HOURS),
                "Europe/Belgrade", null, null, null, null, null, 500, List.of(),
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
