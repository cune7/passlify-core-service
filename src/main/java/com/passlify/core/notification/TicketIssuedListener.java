package com.passlify.core.notification;

import com.passlify.core.issuance.Ticket;
import com.passlify.core.issuance.TicketRepository;
import com.passlify.core.order.Order;
import com.passlify.core.order.OrderRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends ticket emails AFTER the issuing transaction commits, on a separate thread,
 * so a slow/failing SMTP never blocks or rolls back checkout. Runs in its own
 * read-only transaction to load the order + tickets (and render PDFs).
 */
@Component
public class TicketIssuedListener {

    private static final Logger log = LoggerFactory.getLogger(TicketIssuedListener.class);

    private final OrderRepository orders;
    private final TicketRepository tickets;
    private final EmailService emailService;

    public TicketIssuedListener(OrderRepository orders, TicketRepository tickets, EmailService emailService) {
        this.orders = orders;
        this.tickets = tickets;
        this.emailService = emailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public void onTicketsIssued(TicketsIssuedEvent event) {
        Order order = orders.findById(event.orderId()).orElse(null);
        if (order == null) {
            return;
        }
        List<Ticket> issued = tickets.findByOrderIdOrderBySerialNumberAsc(event.orderId());
        log.debug("Delivering {} ticket(s) for order {}", issued.size(), event.orderId());
        emailService.sendTickets(order, issued);
    }
}
