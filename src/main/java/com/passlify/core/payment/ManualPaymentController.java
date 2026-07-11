package com.passlify.core.payment;

import com.passlify.core.payment.dto.ManualPaymentInstructions;
import com.passlify.core.payment.dto.RefundOrderRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual (bank-transfer / offline) payments: public payment instructions for the buyer,
 * and organizer/manager (or admin) confirm/reject actions once the transfer is
 * reconciled. Authorization for confirm/reject is the event {@code MANAGE_PAYMENTS}
 * capability, enforced in {@link PaymentService}.
 */
@RestController
public class ManualPaymentController {

    private final PaymentService paymentService;

    public ManualPaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/api/v1/public/payments/manual/instructions/{orderId}")
    public ManualPaymentInstructions instructions(@PathVariable UUID orderId) {
        return paymentService.manualInstructions(orderId);
    }

    @PostMapping("/api/v1/orders/{orderId}/payment/confirm")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> confirm(@PathVariable UUID orderId) {
        paymentService.confirmManualPayment(orderId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/orders/{orderId}/payment/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> reject(@PathVariable UUID orderId) {
        paymentService.rejectManualPayment(orderId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/api/v1/orders/{orderId}/refund")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> refund(@PathVariable UUID orderId,
                                       @Valid @RequestBody(required = false) RefundOrderRequest req) {
        paymentService.refund(orderId, req == null ? null : req.amountMinor());
        return ResponseEntity.ok().build();
    }
}

