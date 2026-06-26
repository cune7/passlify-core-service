package com.passlify.core.notification;

import java.util.UUID;

/** Published after tickets are issued for an order; triggers email delivery after commit. */
public record TicketsIssuedEvent(UUID orderId) {
}
