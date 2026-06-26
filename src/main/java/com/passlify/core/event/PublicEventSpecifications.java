package com.passlify.core.event;

import jakarta.persistence.criteria.JoinType;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Criteria fragments for the public catalog search. Joins to location/eventType
 * are LEFT joins so an event without a location isn't silently dropped when an
 * unrelated filter is applied. Combine with {@link Specification#allOf}.
 */
final class PublicEventSpecifications {

    private PublicEventSpecifications() {
    }

    static Specification<Event> publishedAndPublic() {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), EventStatus.PUBLISHED),
                cb.equal(root.get("visibility"), Visibility.PUBLIC));
    }

    static Specification<Event> nameContains(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String pattern = "%" + q.toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern);
    }

    static Specification<Event> inCity(String city) {
        if (city == null || city.isBlank()) {
            return null;
        }
        String wanted = city.toLowerCase(Locale.ROOT);
        return (root, query, cb) ->
                cb.equal(cb.lower(root.join("location", JoinType.LEFT).get("city")), wanted);
    }

    static Specification<Event> ofEventType(UUID eventTypeId) {
        if (eventTypeId == null) {
            return null;
        }
        return (root, query, cb) ->
                cb.equal(root.join("eventType", JoinType.LEFT).get("id"), eventTypeId);
    }

    static Specification<Event> startsFrom(Instant from) {
        if (from == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startsAt"), from);
    }

    static Specification<Event> startsUntil(Instant to) {
        if (to == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startsAt"), to);
    }
}
