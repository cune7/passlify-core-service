package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.passlify.core.payment.PaymentProvider;
import com.passlify.core.support.AbstractIntegrationTest;
import com.passlify.core.ticket.TicketType;
import com.passlify.core.ticket.TicketTypeRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Boots against a real Postgres: if the context loads at all, Flyway applied V1/V2
 * and {@code ddl-auto=validate} confirmed every entity matches the schema. The
 * persistence checks additionally exercise the {@code text[]} array mapping and
 * the event → ticket-type relationship round-tripping through the DB.
 */
class SchemaMigrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventRepository events;

    @Autowired
    EventTypeRepository eventTypes;

    @Autowired
    TicketTypeRepository ticketTypes;

    @Test
    void migrationsApplyAndSeedDataLoads() {
        // V2 seeds 15 reference rows.
        assertThat(eventTypes.count()).isEqualTo(15);
    }

    @Test
    void persistsEventWithTagsAndTicketType() {
        Event event = newEvent("tags-roundtrip");
        event.setTags(List.of("festival", "summer"));
        Event saved = events.save(event);

        // Re-read in a fresh query so the array genuinely round-trips through Postgres.
        Event reloaded = events.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getTags()).containsExactly("festival", "summer");
        assertThat(reloaded.getCurrency()).isEqualTo("RSD");
        assertThat(reloaded.getStatus()).isEqualTo(EventStatus.DRAFT);
        assertThat(reloaded.getPaymentProvider()).isEqualTo(PaymentProvider.NONE);
        assertThat(reloaded.getCreatedAt()).isNotNull();

        TicketType vip = new TicketType();
        vip.setEvent(reloaded);
        vip.setName("VIP");
        vip.setPriceMinor(1_200_000L);   // 12,000.00 RSD in para
        vip.setCurrency("RSD");
        vip.setTotalQuantity(200);
        TicketType savedTt = ticketTypes.save(vip);

        TicketType ttReloaded = ticketTypes.findById(savedTt.getId()).orElseThrow();
        assertThat(ttReloaded.getEvent().getId()).isEqualTo(reloaded.getId());
        assertThat(ttReloaded.availableQuantity()).isEqualTo(200);
        assertThat(ttReloaded.getPriceMinor()).isEqualTo(1_200_000L);
    }

    private Event newEvent(String slug) {
        Event e = new Event();
        e.setName("Test Event");
        e.setSlug(slug);
        e.setCurrency("RSD");
        e.setOrganizerId("organizer-sub-1");
        Instant start = Instant.now().plus(30, ChronoUnit.DAYS);
        e.setStartsAt(start);
        e.setEndsAt(start.plus(4, ChronoUnit.HOURS));
        return e;
    }
}
