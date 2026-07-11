package com.passlify.core.event;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventTypeRepository extends JpaRepository<EventType, UUID> {

    /** Active types (categories + leaves), ordered for building the picker tree. */
    List<EventType> findByActiveTrueOrderBySortOrderAscNameAsc();
}
