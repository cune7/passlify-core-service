package com.passlify.core.payment.gateway;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.payment.PaymentProvider;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Resolves the {@link PaymentGateway} for a {@link PaymentProvider}. */
@Component
public class PaymentGatewayRegistry {

    private final Map<PaymentProvider, PaymentGateway> gateways;

    public PaymentGatewayRegistry(List<PaymentGateway> gatewayBeans) {
        this.gateways = gatewayBeans.stream()
                .collect(Collectors.toMap(PaymentGateway::provider, Function.identity()));
    }

    public PaymentGateway require(PaymentProvider provider) {
        PaymentGateway gateway = gateways.get(provider);
        if (gateway == null) {
            throw ApiException.invalidState("No payment gateway configured for provider " + provider);
        }
        return gateway;
    }
}
