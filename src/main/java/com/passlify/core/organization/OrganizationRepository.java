package com.passlify.core.organization;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {

    Optional<Organization> findByOwnerId(String ownerId);

    boolean existsByOwnerId(String ownerId);
}
