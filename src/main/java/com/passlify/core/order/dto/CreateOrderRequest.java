package com.passlify.core.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Create an order. The client sends only ticket-type ids and quantities — never
 * prices (server-authoritative pricing, DOMAIN §4.1). All items must belong to a
 * single event.
 *
 * <p>Custom field values (master spec §13) ride along: {@code buyer.fields} carries
 * PER_PURCHASE values; for {@code EACH_TICKET} ticket types, each line provides one
 * {@code Attendee} per quantity unit with its PER_ATTENDEE {@code fields}.
 */
public record CreateOrderRequest(
        @NotNull @Valid Buyer buyer,
        @NotEmpty @Valid List<Line> items,
        @Size(max = 1024) String returnUrl) {

    public record Buyer(
            @NotBlank @Email @Size(max = 320) String email,
            @Size(max = 256) String name,
            Map<String, String> fields) {
    }

    public record Line(
            @NotNull UUID ticketTypeId,
            @NotNull @Positive Integer quantity,
            @Valid List<Attendee> attendees) {
    }

    public record Attendee(
            @Size(max = 256) String name,
            @Email @Size(max = 320) String email,
            @Size(max = 40) String phone,
            Map<String, String> fields) {
    }
}
