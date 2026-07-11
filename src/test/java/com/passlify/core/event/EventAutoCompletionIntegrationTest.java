package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.passlify.core.support.AbstractIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The scheduled sweep auto-completes PUBLISHED events past endsAt + grace (default 24h),
 * honours a per-event grace override, and leaves recent / DRAFT / CANCELLED events alone.
 */
class EventAutoCompletionIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventRepository events;

    @Autowired
    EventCompletionService completionService;

    @Test
    void completesEndedPublishedEventsPastDefaultGrace() {
        UUID old = persist("autocomplete-old", EventStatus.PUBLISHED, hoursAgo(48), null);
        UUID recent = persist("autocomplete-recent", EventStatus.PUBLISHED, hoursAgo(1), null);
        UUID draft = persist("autocomplete-draft", EventStatus.DRAFT, hoursAgo(48), null);

        completionService.completeEndedEvents();

        assertThat(status(old)).isEqualTo(EventStatus.COMPLETED);       // ended 48h ago > 24h grace
        assertThat(status(recent)).isEqualTo(EventStatus.PUBLISHED);    // within grace
        assertThat(status(draft)).isEqualTo(EventStatus.DRAFT);         // never auto-completed
    }

    @Test
    void honoursPerEventGraceOverride() {
        UUID zeroGrace = persist("autocomplete-zero", EventStatus.PUBLISHED, hoursAgo(1), 0);
        UUID defaultGrace = persist("autocomplete-default", EventStatus.PUBLISHED, hoursAgo(1), null);

        completionService.completeEndedEvents();

        assertThat(status(zeroGrace)).isEqualTo(EventStatus.COMPLETED);   // grace 0 → completes right after end
        assertThat(status(defaultGrace)).isEqualTo(EventStatus.PUBLISHED); // 1h < 24h default
    }

    private EventStatus status(UUID id) {
        return events.findById(id).orElseThrow().getStatus();
    }

    private static Instant hoursAgo(int h) {
        return Instant.now().minus(h, ChronoUnit.HOURS);
    }

    private UUID persist(String slug, EventStatus status, Instant endsAt, Integer graceOverride) {
        Event e = new Event();
        e.setName("Auto Complete " + slug);
        e.setSlug(slug + "-" + UUID.randomUUID().toString().substring(0, 8));
        e.setStatus(status);
        e.setVisibility(Visibility.PUBLIC);
        e.setCurrency("RSD");
        e.setOrganizerId("organizer-1");
        e.setStartsAt(endsAt.minus(2, ChronoUnit.HOURS));
        e.setEndsAt(endsAt);
        e.setAutoCompleteGraceHours(graceOverride);
        return events.save(e).getId();
    }
}
