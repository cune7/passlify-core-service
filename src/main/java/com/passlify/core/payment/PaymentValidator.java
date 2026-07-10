package com.passlify.core.payment;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.order.Order;
import com.passlify.core.order.OrderStatus;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Payment precondition checks: an order must be awaiting payment before a session
 * is created, and the provider name must resolve to a known provider. Injected into
 * {@link PaymentService}.
 */
@Component
public class PaymentValidator {

    public void requireAwaitingPayment(Order order) {
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw ApiException.invalidState("Order is not awaiting payment (status " + order.getStatus() + ")");
        }
    }

    /** Resolves a provider name to the enum, or throws (missing → INVALID_STATE, unknown → NOT_FOUND). */
    public PaymentProvider requireProvider(String name) {
        if (name == null) {
            throw ApiException.invalidState("Order has no payment provider set");
        }
        try {
            return PaymentProvider.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.notFound("Unknown payment provider: " + name);
        }
    }
}
