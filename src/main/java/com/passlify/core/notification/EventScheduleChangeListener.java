package com.passlify.core.notification;

import com.passlify.core.event.EventDomainEvent;
import com.passlify.core.issuance.TicketRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Emails ticket holders when a published event's schedule or venue changes
 * (EVENT_DOMAIN_SPEC §16.3). Runs after the event edit commits, so the ticket set is
 * consistent; each send is best-effort (failures are logged, not thrown).
 */
@Component
public class EventScheduleChangeListener {

    private static final Logger log = LoggerFactory.getLogger(EventScheduleChangeListener.class);

    private final TicketRepository tickets;
    private final EmailService email;

    public EventScheduleChangeListener(TicketRepository tickets, EmailService email) {
        this.tickets = tickets;
        this.email = email;
    }

    @TransactionalEventListener
    public void onScheduleChanged(EventDomainEvent.ScheduleChanged event) {
        List<String> holders = tickets.findHolderEmailsByEventId(event.eventId());
        for (String to : holders) {
            if (to != null && !to.isBlank()) {
                email.sendScheduleChange(to, event.eventName(), event.summary());
            }
        }
        if (!holders.isEmpty()) {
            log.info("Notified {} holder(s) of schedule change for event {}",
                    holders.size(), event.publicId());
        }
    }
}
