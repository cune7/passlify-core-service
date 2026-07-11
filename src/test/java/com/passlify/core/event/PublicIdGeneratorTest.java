package com.passlify.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PublicIdGeneratorTest {

    private final PublicIdGenerator generator = new PublicIdGenerator();

    @Test
    void producesTwentySixCharCrockfordIds() {
        String id = generator.newId();
        assertThat(id).hasSize(26).matches("[0-9A-HJKMNP-TV-Z]{26}");
    }

    @Test
    void idsAreUniqueAcrossManyCalls() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(seen.add(generator.newId())).isTrue();
        }
    }

    @Test
    void idsAreLexicographicallySortableByTime() {
        String earlier = generator.newId(1_000_000_000_000L);
        String later = generator.newId(1_000_000_001_000L);
        assertThat(earlier.compareTo(later)).isNegative();
    }
}
