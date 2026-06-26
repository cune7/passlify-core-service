package com.passlify.core.order;

import com.passlify.core.common.domain.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

/**
 * A purchase — one per checkout attempt. There is no {@code event_id} column;
 * the order links to its event via {@code OrderItem → TicketType → Event}. MVP
 * enforces all items belong to a single event in {@link CheckoutService}.
 * Money is server-computed (DOMAIN §4.1) and stored in integer minor units.
 */
@Getter
@Setter
@Entity
@Table(name = "orders")
public class Order extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_PAYMENT;

    @Column(name = "customer_id", length = 64)
    private String customerId;

    @Column(name = "customer_email", nullable = false, length = 320)
    private String customerEmail;

    @Column(name = "customer_name", length = 256)
    private String customerName;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "subtotal_minor", nullable = false)
    private long subtotalMinor;

    @Column(name = "discount_minor", nullable = false)
    private long discountMinor;

    @Column(name = "tax_minor", nullable = false)
    private long taxMinor;

    @Column(name = "total_minor", nullable = false)
    private long totalMinor;

    @Column(length = 50)
    private String provider;

    @Column(name = "provider_intent_id", length = 255)
    private String providerIntentId;

    @Column(name = "return_url", length = 1024)
    private String returnUrl;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    /** PER_PURCHASE custom field values, as a JSON object keyed by field key. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "buyer_fields", columnDefinition = "jsonb")
    private String buyerFields;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        item.setOrder(this);
        items.add(item);
    }
}
