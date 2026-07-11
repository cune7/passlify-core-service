package com.passlify.core.event;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventCollaboratorRepository extends JpaRepository<EventCollaborator, UUID> {

    List<EventCollaborator> findByEventIdOrderByInvitedAtAsc(UUID eventId);

    Optional<EventCollaborator> findByIdAndEventId(UUID id, UUID eventId);

    Optional<EventCollaborator> findByEventIdAndEmailIgnoreCase(UUID eventId, String email);

    Optional<EventCollaborator> findByEventIdAndUserId(UUID eventId, String userId);
}
