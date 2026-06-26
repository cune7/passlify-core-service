package com.passlify.core.order;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendeeRepository extends JpaRepository<Attendee, UUID> {

    List<Attendee> findByOrderId(UUID orderId);

    List<Attendee> findByOrderIdAndTicketTypeIdOrderByCreatedAtAsc(UUID orderId, UUID ticketTypeId);
}
