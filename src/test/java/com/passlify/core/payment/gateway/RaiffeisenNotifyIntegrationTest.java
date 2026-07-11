package com.passlify.core.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.passlify.core.support.AbstractIntegrationTest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Enables the Raiffeisen/UPC gateway (with generated keys) and drives the NOTIFY_URL
 * handshake through the real controller — verifying the conditional beans wire and the
 * signature-verified response is {@code approve} / {@code reverse}.
 */
@AutoConfigureMockMvc
class RaiffeisenNotifyIntegrationTest extends AbstractIntegrationTest {

    private static final KeyPair BANK = UpcSignatureTest.generate();
    private static final KeyPair MERCHANT = UpcSignatureTest.generate();

    @DynamicPropertySource
    static void raiffeisen(DynamicPropertyRegistry r) {
        r.add("passlify.raiffeisen.enabled", () -> "true");
        r.add("passlify.raiffeisen.gateway-url", () -> "https://ecg.test/go/enter");
        r.add("passlify.raiffeisen.merchant-id", () -> "1753019");
        r.add("passlify.raiffeisen.terminal-id", () -> "E7881019");
        r.add("passlify.raiffeisen.private-key",
                () -> UpcSignatureTest.pem("PRIVATE KEY", MERCHANT.getPrivate().getEncoded()));
        r.add("passlify.raiffeisen.gateway-public-key",
                () -> UpcSignatureTest.pem("PUBLIC KEY", BANK.getPublic().getEncoded()));
    }

    @Autowired
    MockMvc mvc;

    @Test
    void approvedNotifyIsVerifiedAndAcknowledged() throws Exception {
        String body = mvc.perform(post("/api/v1/webhooks/raiffeisen/notify")
                        .contentType(MediaType.TEXT_PLAIN).content(signedCallback("000")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Response.action=approve").contains("OrderID=ORDER-1");
    }

    @Test
    void tamperedNotifyIsReversed() throws Exception {
        String tampered = signedCallback("000").replace("TotalAmount=500", "TotalAmount=999");
        String body = mvc.perform(post("/api/v1/webhooks/raiffeisen/notify")
                        .contentType(MediaType.TEXT_PLAIN).content(tampered))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Response.action=reverse");
    }

    private String signedCallback(String tranCode) {
        String m = "1753019", t = "E7881019", pt = "190101120000", oid = "ORDER-1",
                xid = "XID-1", cur = "941", amt = "500", sd = "", appr = "APPR01";
        String datafile = String.join(";", m, t, pt, oid, xid, cur, amt, sd, tranCode, appr) + ";";
        String sig = UpcSignature.sign(datafile, BANK.getPrivate());
        return "MerchantID=" + m + "&TerminalID=" + t + "&PurchaseTime=" + pt + "&OrderID=" + oid
                + "&XID=" + xid + "&Currency=" + cur + "&TotalAmount=" + amt + "&SD=" + sd
                + "&TranCode=" + tranCode + "&ApprovalCode=" + appr
                + "&Signature=" + URLEncoder.encode(sig, StandardCharsets.UTF_8);
    }
}
