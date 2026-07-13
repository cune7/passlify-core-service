package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.passlify.core.support.AbstractIntegrationTest;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Verifies the V19 seed produced the EVENT_DOMAIN_SPEC §19.1 catalog. */
class EventTypeCatalogIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    EventTypeRepository eventTypes;

    @Test
    void seedsTheSixTopLevelCategoriesAsNonSelectableHeadings() {
        Map<String, EventType> active = activeByCode();

        assertThat(active).containsKeys(
                "SPORT", "MUSIC", "CULTURE", "BUSINESS", "OTHER", "PRIVATE_EVENT");
        assertThat(List.of("SPORT", "MUSIC", "CULTURE", "BUSINESS", "OTHER", "PRIVATE_EVENT"))
                .allSatisfy(code -> {
                    EventType c = active.get(code);
                    assertThat(c.isSelectable()).isFalse();
                    assertThat(c.getParent()).isNull();
                });
    }

    @Test
    void seedsLeavesUnderTheCorrectCategory() {
        Map<String, EventType> active = activeByCode();

        assertThat(active).containsKeys(
                "SPORT.PAINTBALL", "MUSIC.FESTIVAL", "CULTURE.STANDUP",
                "BUSINESS.CONFERENCE", "OTHER.WORKSHOP", "PRIVATE_EVENT.PRIVATE_EVENT");

        EventType festival = active.get("MUSIC.FESTIVAL");
        assertThat(festival.isSelectable()).isTrue();
        assertThat(festival.getParent().getCode()).isEqualTo("MUSIC");
        assertThat(active.get("BUSINESS.CONFERENCE").getParent().getCode()).isEqualTo("BUSINESS");
    }

    @Test
    void deactivatesTheObsoleteProvisionalTypes() {
        Map<String, EventType> active = activeByCode();

        // Old V2/V9 catalog entries that are not part of §19.1 must be gone from the picker.
        assertThat(active).doesNotContainKeys(
                "THEATRE", "COMEDY", "FILM", "FAMILY", "CONFERENCE",
                "MUSIC.CONCERT", "MUSIC.CLUB_NIGHT", "SPORT.MATCH");
    }

    private Map<String, EventType> activeByCode() {
        return eventTypes.findByActiveTrueOrderBySortOrderAscNameAsc().stream()
                .collect(Collectors.toMap(EventType::getCode, Function.identity()));
    }
}
