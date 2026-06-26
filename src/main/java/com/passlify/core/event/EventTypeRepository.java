package com.passlify.core.event;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventTypeRepository extends JpaRepository<EventType, UUID> {
}
