package com.passlify.core.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventAccessGrantRepository extends JpaRepository<EventAccessGrant, UUID> {

    Optional<EventAccessGrant> findByToken(String token);

    Optional<EventAccessGrant> findByIdAndEventId(UUID id, UUID eventId);

    List<EventAccessGrant> findByEventIdOrderByCreatedAtAsc(UUID eventId);
}
