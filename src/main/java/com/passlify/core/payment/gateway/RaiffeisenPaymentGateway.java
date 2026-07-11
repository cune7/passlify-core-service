package com.passlify.core.payment.gateway;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.order.Order;
import com.passlify.core.payment.PaymentProvider;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Raiffeisen Serbia card payments via the UPC "e-Commerce Connect Gateway"
 * (EVENT_DOMAIN_SPEC §10). Redirect-based: the buyer is sent to the gateway's secure
 * page with RSA-SHA1-signed fields; the result comes back on the pre-registered
 * NOTIFY_URL/SUCCESS_URL, which we verify against the gateway's public key.
 *
 * <p>Activated only when {@code passlify.raiffeisen.enabled=true} + a private key is
 * configured, so the app runs without it in dev/test. SUCCESS/FAILURE/NOTIFY URLs are
 * registered per-terminal at the bank, not sent per request.
 *
 * <p><b>Scope note (needs live verification):</b> the request/response datafile field
 * order below follows the interface doc (§3–5, §7); confirm against the UPC test server
 * with real merchant credentials. Two HTTP pieces are still to wire for a live flow:
 * the POST-form redirect page and the NOTIFY_URL {@code Response.action=approve}
 * handshake (tracked separately). Tokenization (saved cards) is not implemented.
 */
@Component
@ConditionalOnProperty(prefix = "passlify.raiffeisen", name = "enabled", havingValue = "true")
public class RaiffeisenPaymentGateway implements PaymentGateway {

    /** ISO-4217 numeric currency codes (§4). */
    private static final Map<String, String> CURRENCY_CODES =
            Map.of("RSD", "941", "EUR", "978", "USD", "840", "RUB", "643", "UAH", "980");
    private static final DateTimeFormatter PURCHASE_TIME =
            DateTimeFormatter.ofPattern("yyMMddHHmmss").withZone(ZoneId.systemDefault());

    private final String gatewayUrl;
    private final String merchantId;
    private final String terminalId;
    private final String locale;
    private final PrivateKey privateKey;
    private final PublicKey gatewayPublicKey;

    public RaiffeisenPaymentGateway(
            @Value("${passlify.raiffeisen.gateway-url}") String gatewayUrl,
            @Value("${passlify.raiffeisen.merchant-id}") String merchantId,
            @Value("${passlify.raiffeisen.terminal-id}") String terminalId,
            @Value("${passlify.raiffeisen.locale:en}") String locale,
            @Value("${passlify.raiffeisen.private-key}") String privateKeyPem,
            @Value("${passlify.raiffeisen.gateway-public-key}") String gatewayPublicKeyPem) {
        this.gatewayUrl = gatewayUrl;
        this.merchantId = merchantId;
        this.terminalId = terminalId;
        this.locale = locale;
        this.privateKey = UpcSignature.loadPrivateKey(privateKeyPem);
        this.gatewayPublicKey = UpcSignature.loadPublicKey(gatewayPublicKeyPem);
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.RAIFFEISEN;
    }

    @Override
    public CheckoutSession createSession(Order order, String successUrl, String cancelUrl) {
        String orderRef = orderRef(order);
        String purchaseTime = PURCHASE_TIME.format(Instant.now());
        String amount = String.valueOf(order.getTotalMinor()); // smallest currency units (§4)
        String currency = currencyCode(order.getCurrency());

        // Request signature datafile (§3): MerchantId;TerminalId;PurchaseTime;OrderId;Currency;Amount;SD;
        String datafile = String.join(";",
                merchantId, terminalId, purchaseTime, orderRef, currency, amount, "") + ";";
        String signature = UpcSignature.sign(datafile, privateKey);

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Version", "1");
        fields.put("MerchantID", merchantId);
        fields.put("TerminalID", terminalId);
        fields.put("TotalAmount", amount);
        fields.put("Currency", currency);
        fields.put("PurchaseTime", purchaseTime);
        fields.put("OrderID", orderRef);
        fields.put("locale", locale);
        fields.put("Signature", signature);

        String checkoutUrl = gatewayUrl + "?" + urlEncode(fields);
        return new CheckoutSession(orderRef, checkoutUrl, null);
    }

    @Override
    public PaymentEvent verifyAndParse(String rawBody, String signature) {
        Map<String, String> p = parseForm(rawBody);
        // Response signature datafile (§Signature Verification):
        // MerchantId;TerminalId;PurchaseTime;OrderId;Xid;Currency;Amount;SD;TranCode;ApprovalCode;
        String datafile = String.join(";",
                nz(p.get("MerchantID")), nz(p.get("TerminalID")), nz(p.get("PurchaseTime")),
                nz(p.get("OrderID")), nz(p.get("XID")), nz(p.get("Currency")), nz(p.get("TotalAmount")),
                nz(p.get("SD")), nz(p.get("TranCode")), nz(p.get("ApprovalCode"))) + ";";
        if (!UpcSignature.verify(datafile, p.get("Signature"), gatewayPublicKey)) {
            throw ApiException.of(ErrorCode.BAD_SIGNATURE, "Invalid Raiffeisen callback signature");
        }

        String orderRef = p.get("OrderID");
        String xid = p.get("XID");
        boolean approved = "000".equals(p.get("TranCode")); // §8: 000 = successful authorization
        PaymentEvent.Type type = approved ? PaymentEvent.Type.PAID : PaymentEvent.Type.FAILED;
        String eventId = "rba_" + orderRef + "_" + (xid != null ? xid : p.get("TranCode"));
        return new PaymentEvent(eventId, type, orderRef, xid, p.get("Rrn"), null);
    }

    /** OrderID must be ≤20 chars (§4); a UUID's hex prefix is stable and unique enough. */
    private String orderRef(Order order) {
        return order.getId().toString().replace("-", "").substring(0, 20);
    }

    private String currencyCode(String currency) {
        String code = CURRENCY_CODES.get(currency == null ? "" : currency.toUpperCase(Locale.ROOT));
        if (code == null) {
            throw ApiException.invalidState("Raiffeisen does not support currency " + currency);
        }
        return code;
    }

    private static String nz(String v) {
        return v == null ? "" : v;
    }

    private static String urlEncode(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new LinkedHashMap<>();
        if (body == null || body.isBlank()) {
            return map;
        }
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            map.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return map;
    }
}
