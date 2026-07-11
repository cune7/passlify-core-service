package com.passlify.core.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.order.Order;
import com.passlify.core.payment.PaymentProvider;
import java.security.KeyPair;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RaiffeisenPaymentGatewayTest {

    // Merchant signs requests; the gateway (its own keypair) signs responses we verify.
    private final KeyPair merchant = UpcSignatureTest.generate();
    private final KeyPair bank = UpcSignatureTest.generate();

    private final RaiffeisenPaymentGateway gateway = new RaiffeisenPaymentGateway(
            "https://ecg.test/go/enter", "1753019", "E7881019", "en",
            UpcSignatureTest.pem("PRIVATE KEY", merchant.getPrivate().getEncoded()),
            UpcSignatureTest.pem("PUBLIC KEY", bank.getPublic().getEncoded()));

    @Test
    void createSessionBuildsSignedRedirectFields() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setTotalMinor(150_000L);
        order.setCurrency("RSD");

        CheckoutSession session = gateway.createSession(order, null, null);
        assertThat(gateway.provider()).isEqualTo(PaymentProvider.RAIFFEISEN);
        assertThat(session.checkoutUrl()).startsWith("https://ecg.test/go/enter?");
        assertThat(session.checkoutUrl())
                .contains("Currency=941").contains("TotalAmount=150000")
                .contains("MerchantID=1753019").contains("Signature=");
        assertThat(session.sessionId()).hasSize(20);
    }

    @Test
    void verifyAndParseAcceptsApprovedSignedCallback() {
        String body = signedCallback("000", "txn-9");
        PaymentEvent event = gateway.verifyAndParse(body, null);
        assertThat(event.type()).isEqualTo(PaymentEvent.Type.PAID);
        assertThat(event.sessionId()).isEqualTo("ORDER123");
        assertThat(event.intentId()).isEqualTo("txn-9");
    }

    @Test
    void declinedTranCodeMapsToFailed() {
        assertThat(gateway.verifyAndParse(signedCallback("116", "txn-1"), null).type())
                .isEqualTo(PaymentEvent.Type.FAILED);
    }

    @Test
    void tamperedCallbackIsRejected() {
        String tampered = signedCallback("000", "txn-9").replace("TotalAmount=500", "TotalAmount=999");
        assertThatThrownBy(() -> gateway.verifyAndParse(tampered, null)).isInstanceOf(ApiException.class);
    }

    /** Builds a NOTIFY body signed with the bank key, matching the gateway's response datafile order. */
    private String signedCallback(String tranCode, String xid) {
        String merchantId = "1753019", terminalId = "E7881019", purchaseTime = "190101120000";
        String orderId = "ORDER123", currency = "941", amount = "500", sd = "", approvalCode = "APPR01";
        String datafile = String.join(";",
                merchantId, terminalId, purchaseTime, orderId, xid, currency, amount, sd, tranCode, approvalCode) + ";";
        String signature = UpcSignature.sign(datafile, bank.getPrivate());
        return "MerchantID=" + merchantId + "&TerminalID=" + terminalId + "&PurchaseTime=" + purchaseTime
                + "&OrderID=" + orderId + "&XID=" + xid + "&Currency=" + currency + "&TotalAmount=" + amount
                + "&SD=" + sd + "&TranCode=" + tranCode + "&ApprovalCode=" + approvalCode
                + "&Signature=" + java.net.URLEncoder.encode(signature, java.nio.charset.StandardCharsets.UTF_8);
    }
}
