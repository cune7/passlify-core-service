package com.passlify.core.notification;

import com.passlify.core.issuance.Ticket;
import com.passlify.core.issuance.pdf.TicketPdfService;
import com.passlify.core.order.Order;
import jakarta.mail.internet.MimeMessage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends issued tickets to the buyer (one PDF attachment per ticket). Failures are
 * logged, never thrown — a flaky SMTP must not break the paid/issued flow (tickets
 * remain fetchable via the API regardless).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TicketPdfService ticketPdfService;
    private final String from;
    private final String baseUrl;
    private final boolean enabled;

    public EmailService(JavaMailSender mailSender,
                        TicketPdfService ticketPdfService,
                        @Value("${passlify.mail.from:tickets@passlify.rs}") String from,
                        @Value("${passlify.base-url:http://localhost:8081}") String baseUrl,
                        @Value("${passlify.mail.enabled:true}") boolean enabled) {
        this.mailSender = mailSender;
        this.ticketPdfService = ticketPdfService;
        this.from = from;
        this.baseUrl = baseUrl;
        this.enabled = enabled;
    }

    public void sendTickets(Order order, List<Ticket> tickets) {
        if (!enabled) {
            log.debug("Mail disabled; skipping ticket email for order {}", order.getId());
            return;
        }
        if (tickets.isEmpty()) {
            return;
        }
        String eventName = tickets.get(0).getEvent().getName();
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(order.getCustomerEmail());
            helper.setSubject("Your tickets — " + eventName);
            helper.setText(body(order, tickets, eventName));
            for (Ticket ticket : tickets) {
                helper.addAttachment(ticket.getSerialNumber() + ".pdf",
                        new ByteArrayResource(ticketPdfService.render(ticket)), "application/pdf");
            }
            mailSender.send(message);
            log.info("Sent {} ticket(s) to {} for order {}", tickets.size(), order.getCustomerEmail(), order.getId());
        } catch (MailException | jakarta.mail.MessagingException e) {
            log.warn("Failed to send ticket email for order {} ({}); tickets remain available via the API",
                    order.getId(), e.getMessage());
        }
    }

    private String body(Order order, List<Ticket> tickets, String eventName) {
        StringBuilder sb = new StringBuilder();
        sb.append("Hi").append(order.getCustomerName() != null ? " " + order.getCustomerName() : "").append(",\n\n");
        sb.append("Thanks for your purchase. Your ticket(s) for \"").append(eventName).append("\" are attached.\n\n");
        for (Ticket ticket : tickets) {
            sb.append("• ").append(ticket.getTicketType().getName())
                    .append(" — ").append(ticket.getSerialNumber()).append('\n')
                    .append("  PDF: ").append(baseUrl).append("/api/v1/tickets/").append(ticket.getId()).append("/pdf\n");
        }
        sb.append("\nShow the QR code at the entrance. See you there!\n");
        return sb.toString();
    }
}
