package com.passlify.core.event;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSettingsRepository extends JpaRepository<EventSettings, UUID> {
}
