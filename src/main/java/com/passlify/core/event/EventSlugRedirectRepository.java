package com.passlify.core.event;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventSlugRedirectRepository extends JpaRepository<EventSlugRedirect, UUID> {

    Optional<EventSlugRedirect> findByOldSlug(String oldSlug);
}
