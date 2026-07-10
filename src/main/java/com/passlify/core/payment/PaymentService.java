package com.passlify.core.payment;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.issuance.TicketIssuanceService;
import com.passlify.core.order.Order;
import com.passlify.core.order.OrderItem;
import com.passlify.core.order.OrderRepository;
import com.passlify.core.order.OrderStatus;
import com.passlify.core.payment.gateway.CheckoutSession;
import com.passlify.core.payment.gateway.PaymentEvent;
import com.passlify.core.payment.dto.PaymentSessionResponse;
import com.passlify.core.payment.gateway.PaymentGateway;
import com.passlify.core.payment.gateway.PaymentGatewayRegistry;
import com.passlify.core.ticket.TicketTypeRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment orchestration: creates a hosted checkout session via the order's
 * {@link PaymentGateway}, and processes inbound webhooks idempotently
 * (DOMAIN §4.3). Webhook handling records the event in the ledger before acting;
 * a duplicate (provider, eventId) is ACKed and skipped so fulfillment never
 * double-fires. Ticket issuance on PAID is wired in slice 6.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orders;
    private final PaymentRepository payments;
    private final WebhookEventRepository webhookEvents;
    private final TicketTypeRepository ticketTypes;
    private final PaymentGatewayRegistry gateways;
    private final TicketIssuanceService ticketIssuanceService;
    private final PaymentValidator validator;

    public PaymentService(OrderRepository orders,
                          PaymentRepository payments,
                          WebhookEventRepository webhookEvents,
                          TicketTypeRepository ticketTypes,
                          PaymentGatewayRegistry gateways,
                          TicketIssuanceService ticketIssuanceService,
                          PaymentValidator validator) {
        this.orders = orders;
        this.payments = payments;
        this.webhookEvents = webhookEvents;
        this.ticketTypes = ticketTypes;
        this.gateways = gateways;
        this.ticketIssuanceService = ticketIssuanceService;
        this.validator = validator;
    }

    @Transactional
    public PaymentSessionResponse createSession(UUID orderId, String successUrl, String cancelUrl) {
        Order order = orders.findById(orderId)
                .orElseThrow(() -> ApiException.notFound("Order not found: " + orderId));
        validator.requireAwaitingPayment(order);

        PaymentProvider provider = validator.requireProvider(order.getProvider());
        PaymentGateway gateway = gateways.require(provider);
        CheckoutSession session = gateway.createSession(order, successUrl, cancelUrl);

        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setProvider(provider);
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmountMinor(order.getTotalMinor());
        payment.setCurrency(order.getCurrency());
        payment.setProviderSessionId(session.sessionId());
        payment.setProviderIntentId(session.intentId());

        order.setProviderIntentId(session.sessionId());
        Payment saved = payments.save(payment);
        return new PaymentSessionResponse(saved.getId(), session.checkoutUrl(), session.sessionId());
    }

    /**
     * Verifies + records + processes a provider webhook. Idempotent: a duplicate
     * event is skipped. Bad signatures throw (mapped to 400); everything received
     * and accepted returns normally (the controller ACKs 200).
     */
    @Transactional
    public void handleWebhook(String providerName, String rawBody, String signature) {
        PaymentProvider provider = validator.requireProvider(providerName);
        PaymentGateway gateway = gateways.require(provider);
        PaymentEvent event = gateway.verifyAndParse(rawBody, signature);

        WebhookEventKey key = new WebhookEventKey(event.eventId(), provider);
        if (webhookEvents.existsById(key)) {
            log.debug("Duplicate webhook {} for {} — skipping", event.eventId(), provider);
            return;
        }

        WebhookEvent ledger = new WebhookEvent();
        ledger.setId(event.eventId());
        ledger.setProvider(provider);
        ledger.setType(event.type().name());
        ledger.setPayload(rawBody);
        try {
            webhookEvents.saveAndFlush(ledger);
        } catch (DataIntegrityViolationException raceDuplicate) {
            // A concurrent delivery inserted the same event first — it's a duplicate.
            return;
        }

        switch (event.type()) {
            case PAID -> applyPaid(event);
            case FAILED -> applyFailed(event);
            case REFUNDED -> applyRefunded(event);
            case IGNORED -> log.debug("Ignored webhook {} for {}", event.eventId(), provider);
        }
        ledger.setProcessedAt(Instant.now());
    }

    // ---- event handlers ----------------------------------------------------

    private void applyPaid(PaymentEvent event) {
        Payment payment = findPayment(event);
        if (payment == null) {
            log.warn("PAID webhook with no matching payment (session={}, intent={})",
                    event.sessionId(), event.intentId());
            return;
        }
        if (event.intentId() != null) {
            payment.setProviderIntentId(event.intentId());
        }
        if (event.chargeId() != null) {
            payment.setProviderChargeId(event.chargeId());
        }

        Order order = payment.getOrder();
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            order.setStatus(OrderStatus.PAID);
            order.setPaidAt(Instant.now());
            payment.setStatus(PaymentStatus.SUCCEEDED);
            ticketIssuanceService.issueForOrder(order);   // idempotent
        } else if (order.getStatus() == OrderStatus.PAID) {
            payment.setStatus(PaymentStatus.SUCCEEDED);   // idempotent replay
        } else {
            log.warn("PAID webhook for order {} in unexpected state {}", order.getId(), order.getStatus());
        }
    }

    private void applyFailed(PaymentEvent event) {
        Payment payment = findPayment(event);
        if (payment == null) {
            log.warn("FAILED webhook with no matching payment (session={})", event.sessionId());
            return;
        }
        payment.setStatus(PaymentStatus.FAILED);
        Order order = payment.getOrder();
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            order.setStatus(OrderStatus.FAILED);
            releaseInventory(order);
        }
    }

    /**
     * Refund handling (DOMAIN §4.8). Accumulates the refunded amount; a full refund
     * voids the order's tickets and releases inventory. Already-fully-refunded orders
     * are skipped so a second refund event can't double-release.
     */
    private void applyRefunded(PaymentEvent event) {
        Payment payment = findPayment(event);
        if (payment == null) {
            log.warn("REFUNDED webhook with no matching payment (charge={}, session={})",
                    event.chargeId(), event.sessionId());
            return;
        }
        Order order = payment.getOrder();
        if (order.getStatus() == OrderStatus.REFUNDED) {
            return;   // already fully refunded — idempotent against distinct refund events
        }
        if (event.chargeId() != null) {
            payment.setProviderChargeId(event.chargeId());
        }

        long refundAmount = event.refundedMinor() != null ? event.refundedMinor() : payment.getAmountMinor();
        long totalRefunded = Math.min(payment.getAmountMinor(), payment.getRefundedMinor() + refundAmount);
        payment.setRefundedMinor(totalRefunded);

        boolean full = totalRefunded >= payment.getAmountMinor();
        payment.setStatus(full ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);
        order.setStatus(full ? OrderStatus.REFUNDED : OrderStatus.PARTIALLY_REFUNDED);

        if (full) {
            ticketIssuanceService.voidForOrder(order.getId());
            releaseInventory(order);
        }
    }

    private void releaseInventory(Order order) {
        for (OrderItem item : order.getItems()) {
            ticketTypes.release(item.getTicketType().getId(), item.getQuantity());
        }
    }

    private Payment findPayment(PaymentEvent event) {
        if (event.sessionId() != null) {
            Payment bySession = payments.findByProviderSessionId(event.sessionId()).orElse(null);
            if (bySession != null) {
                return bySession;
            }
        }
        if (event.intentId() != null) {
            return payments.findByProviderIntentId(event.intentId()).orElse(null);
        }
        return null;
    }

}
