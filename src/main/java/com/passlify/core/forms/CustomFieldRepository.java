package com.passlify.core.forms;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFieldRepository extends JpaRepository<CustomField, UUID> {

    List<CustomField> findByEventIdOrderBySortOrderAscCreatedAtAsc(UUID eventId);

    List<CustomField> findByEventIdAndScopeOrderBySortOrderAscCreatedAtAsc(UUID eventId, FieldScope scope);

    Optional<CustomField> findByIdAndEventId(UUID id, UUID eventId);

    boolean existsByEventIdAndFieldKey(UUID eventId, String fieldKey);
}
