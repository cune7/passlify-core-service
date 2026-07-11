package com.passlify.core.event;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EventRepository extends JpaRepository<Event, UUID>, JpaSpecificationExecutor<Event> {

    Optional<Event> findByIdAndOrganizerId(UUID id, String organizerId);

    Page<Event> findByOrganizerId(String organizerId, Pageable pageable);

    Page<Event> findByOrganizerIdAndStatus(String organizerId, EventStatus status, Pageable pageable);

    Optional<Event> findBySlugAndStatusAndVisibility(String slug, EventStatus status, Visibility visibility);

    /** Direct-link resolution: PUBLIC and UNLISTED are reachable by slug; PRIVATE is not (§25.3). */
    Optional<Event> findBySlugAndStatusAndVisibilityIn(
            String slug, EventStatus status, Collection<Visibility> visibilities);

    boolean existsBySlug(String slug);
}
