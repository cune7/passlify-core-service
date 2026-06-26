package com.passlify.core.scan;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketScanRepository extends JpaRepository<TicketScan, UUID> {
}
