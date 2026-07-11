package com.passlify.core.event;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventAuditEntryRepository extends JpaRepository<EventAuditEntry, UUID> {

    Page<EventAuditEntry> findByEventIdOrderByOccurredAtDesc(UUID eventId, Pageable pageable);
}
