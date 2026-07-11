package com.passlify.core.event;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventOnlineAccessRepository extends JpaRepository<EventOnlineAccess, UUID> {
}
