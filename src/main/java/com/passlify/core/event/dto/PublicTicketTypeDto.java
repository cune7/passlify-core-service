package com.passlify.core.event.dto;

import com.passlify.core.ticket.TicketType;
import java.time.Instant;
import java.util.UUID;

/** Buyer-facing ticket type (no internal counters beyond what's needed to buy). */
public record PublicTicketTypeDto(
        UUID id,
        String name,
        String description,
        long priceMinor,
        String currency,
        int availableQuantity,
        int maxPerOrder,
        Instant salesStartAt,
        Instant salesEndAt) {

    public static PublicTicketTypeDto from(TicketType t) {
        return new PublicTicketTypeDto(
                t.getId(), t.getName(), t.getDescription(), t.getPriceMinor(), t.getCurrency(),
                t.availableQuantity(), t.getMaxPerOrder(), t.getSalesStartAt(), t.getSalesEndAt());
    }
}
