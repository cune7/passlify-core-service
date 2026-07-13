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

    // Organizer board — includeArchived=true uses the plain finders (all), false uses ArchivedFalse.
    Page<Event> findByOrganizerId(String organizerId, Pageable pageable);

    Page<Event> findByOrganizerIdAndStatus(String organizerId, EventStatus status, Pageable pageable);

    Page<Event> findByOrganizerIdAndArchivedFalse(String organizerId, Pageable pageable);

    Page<Event> findByOrganizerIdAndStatusAndArchivedFalse(
            String organizerId, EventStatus status, Pageable pageable);

    Optional<Event> findBySlugAndStatusAndVisibility(String slug, EventStatus status, Visibility visibility);

    /**
     * Direct-link resolution: a non-archived event reachable by slug — PUBLIC/UNLISTED,
     * PUBLISHED (upcoming) or COMPLETED (history). PRIVATE/archived are not (§25.3).
     */
    Optional<Event> findBySlugAndArchivedFalseAndStatusInAndVisibilityIn(
            String slug, Collection<EventStatus> statuses, Collection<Visibility> visibilities);

    boolean existsBySlug(String slug);

    /** Published events whose end time has passed — candidates for auto-completion. */
    java.util.List<Event> findByStatusAndEndsAtBefore(EventStatus status, java.time.Instant cutoff);
}
