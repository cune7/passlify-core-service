package com.passlify.core.payment;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link WebhookEvent}: the provider's event id is unique per provider. */
public class WebhookEventKey implements Serializable {

    private String id;
    private PaymentProvider provider;

    public WebhookEventKey() {
    }

    public WebhookEventKey(String id, PaymentProvider provider) {
        this.id = id;
        this.provider = provider;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof WebhookEventKey that)) {
            return false;
        }
        return Objects.equals(id, that.id) && provider == that.provider;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, provider);
    }
}
