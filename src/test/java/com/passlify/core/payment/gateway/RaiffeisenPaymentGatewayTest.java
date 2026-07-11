package com.passlify.core.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.order.Order;
import com.passlify.core.payment.PaymentProvider;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RaiffeisenPaymentGatewayTest {

    private static final String STORE_KEY = "test-store-key";
    private final RaiffeisenPaymentGateway gateway = new RaiffeisenPaymentGateway(
            "https://bank.example/est3Dgate", "100000", STORE_KEY,
            "https://app.example/ok", "https://app.example/fail");

    @Test
    void createSessionBuildsASignedHostedPageUrl() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTotalMinor(150_000L);
        order.setCurrency("RSD");

        CheckoutSession session = gateway.createSession(order, null, null);
        assertThat(gateway.provider()).isEqualTo(PaymentProvider.RAIFFEISEN);
        assertThat(session.checkoutUrl()).startsWith("https://bank.example/est3Dgate?");
        assertThat(session.checkoutUrl()).contains("currency=941").contains("amount=1500.00");
        assertThat(session.sessionId()).isEqualTo(order.getId().toString());
    }

    @Test
    void verifyAndParseAcceptsApprovedCallbackAndRejectsTamperedHash() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("oid", "order-1");
        params.put("Response", "Approved");
        params.put("ProcReturnCode", "00");
        params.put("TransId", "txn-9");
        String body = "oid=order-1&Response=Approved&ProcReturnCode=00&TransId=txn-9&HASH="
                + urlEncode(NestPayHash.compute(params, STORE_KEY));

        PaymentEvent event = gateway.verifyAndParse(body, null);
        assertThat(event.type()).isEqualTo(PaymentEvent.Type.PAID);
        assertThat(event.sessionId()).isEqualTo("order-1");
        assertThat(event.intentId()).isEqualTo("txn-9");

        assertThatThrownBy(() -> gateway.verifyAndParse(body + "tampered", null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void declinedCallbackMapsToFailed() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("oid", "order-2");
        params.put("Response", "Declined");
        String body = "oid=order-2&Response=Declined&HASH="
                + urlEncode(NestPayHash.compute(params, STORE_KEY));

        assertThat(gateway.verifyAndParse(body, null).type()).isEqualTo(PaymentEvent.Type.FAILED);
    }

    private static String urlEncode(String v) {
        return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
    }
}
