package com.passlify.core.dashboard.dto;

import com.passlify.core.ticket.TicketType;
import java.util.List;
import java.util.UUID;

/** Event sales overview for the organizer dashboard. */
public record SalesSummaryResponse(
        String currency,
        long ticketsIssued,
        long ticketsCheckedIn,
        long paidOrders,
        long grossRevenueMinor,
        List<TicketTypeSales> ticketTypes) {

    public record TicketTypeSales(
            UUID id,
            String name,
            long priceMinor,
            int totalQuantity,
            int soldQuantity,
            int availableQuantity) {

        public static TicketTypeSales from(TicketType t) {
            return new TicketTypeSales(
                    t.getId(), t.getName(), t.getPriceMinor(),
                    t.getTotalQuantity(), t.getSoldQuantity(), t.availableQuantity());
        }
    }
}
