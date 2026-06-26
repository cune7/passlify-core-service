package com.passlify.core.dashboard.dto;

import com.passlify.core.order.Order;
import com.passlify.core.order.OrderStatus;
import java.time.Instant;
import java.util.UUID;

/** Compact order row for the organizer orders list. */
public record OrderSummary(
        UUID id,
        OrderStatus status,
        String customerEmail,
        String customerName,
        String currency,
        long totalMinor,
        int ticketCount,
        String provider,
        Instant paidAt,
        Instant createdAt) {

    public static OrderSummary from(Order o) {
        int ticketCount = o.getItems().stream().mapToInt(i -> i.getQuantity()).sum();
        return new OrderSummary(
                o.getId(), o.getStatus(), o.getCustomerEmail(), o.getCustomerName(), o.getCurrency(),
                o.getTotalMinor(), ticketCount, o.getProvider(), o.getPaidAt(), o.getCreatedAt());
    }
}
