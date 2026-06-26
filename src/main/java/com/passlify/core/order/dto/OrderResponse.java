package com.passlify.core.order.dto;

import com.passlify.core.order.Order;
import com.passlify.core.order.OrderItem;
import com.passlify.core.order.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Order view returned on create and status lookup. */
public record OrderResponse(
        UUID id,
        OrderStatus status,
        String customerEmail,
        String customerName,
        String currency,
        long subtotalMinor,
        long discountMinor,
        long taxMinor,
        long totalMinor,
        String provider,
        List<Item> items,
        Instant expiresAt,
        Instant paidAt,
        Instant createdAt) {

    public record Item(
            UUID ticketTypeId,
            String ticketTypeName,
            int quantity,
            long unitPriceMinor,
            long totalPriceMinor) {
    }

    public static OrderResponse from(Order o) {
        List<Item> lines = o.getItems().stream()
                .map(OrderResponse::toItem)
                .toList();
        return new OrderResponse(
                o.getId(), o.getStatus(), o.getCustomerEmail(), o.getCustomerName(), o.getCurrency(),
                o.getSubtotalMinor(), o.getDiscountMinor(), o.getTaxMinor(), o.getTotalMinor(),
                o.getProvider(), lines, o.getExpiresAt(), o.getPaidAt(), o.getCreatedAt());
    }

    private static Item toItem(OrderItem oi) {
        return new Item(
                oi.getTicketType().getId(),
                oi.getTicketType().getName(),
                oi.getQuantity(),
                oi.getUnitPriceMinor(),
                oi.getTotalPriceMinor());
    }
}
