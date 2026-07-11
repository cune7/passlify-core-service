package com.passlify.core.payment.gateway;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.order.Order;
import com.passlify.core.payment.PaymentProvider;
import java.net.URLDecoder;
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
 * (EVENT_DOMAIN_SPEC §10). Redirect-based with RSA-SHA1-signed fields.
 *
 * <p>Flow: {@code createSession} returns a URL to our own redirect page
 * ({@link #renderRedirectForm}) which auto-POSTs the signed fields to the bank; the
 * bank posts the result to the registered NOTIFY_URL, handled with signature
 * verification ({@link #verifyAndParse}) and answered with the required
 * {@code Response.action} handshake ({@link #buildNotifyResponse}).
 *
 * <p>Activated only when {@code passlify.raiffeisen.enabled=true}. SUCCESS/FAILURE/
 * NOTIFY URLs are registered per-terminal at the bank, not sent per request.
 *
 * <p><b>Needs live verification:</b> the request/response datafile field order, the
 * NOTIFY response body format, and result codes follow the interface doc; confirm
 * against the UPC test server with real merchant credentials. Tokenization not done.
 */
@Component
@ConditionalOnProperty(prefix = "passlify.raiffeisen", name = "enabled", havingValue = "true")
public class RaiffeisenPaymentGateway implements PaymentGateway {

    /** ISO-4217 numeric currency codes (§4). */
    private static final Map<String, String> CURRENCY_CODES =
            Map.of("RSD", "941", "EUR", "978", "USD", "840", "RUB", "643", "UAH", "980");
    private static final DateTimeFormatter PURCHASE_TIME =
            DateTimeFormatter.ofPattern("yyMMddHHmmss").withZone(ZoneId.systemDefault());
    public static final String REDIRECT_PATH = "/api/v1/public/payments/raiffeisen/redirect/";

    private final String gatewayUrl;
    private final String merchantId;
    private final String terminalId;
    private final String locale;
    private final String baseUrl;
    private final PrivateKey privateKey;
    private final PublicKey gatewayPublicKey;

    public RaiffeisenPaymentGateway(
            @Value("${passlify.raiffeisen.gateway-url}") String gatewayUrl,
            @Value("${passlify.raiffeisen.merchant-id}") String merchantId,
            @Value("${passlify.raiffeisen.terminal-id}") String terminalId,
            @Value("${passlify.raiffeisen.locale:en}") String locale,
            @Value("${passlify.base-url:http://localhost:8081}") String baseUrl,
            @Value("${passlify.raiffeisen.private-key}") String privateKeyPem,
            @Value("${passlify.raiffeisen.gateway-public-key}") String gatewayPublicKeyPem) {
        this.gatewayUrl = gatewayUrl;
        this.merchantId = merchantId;
        this.terminalId = terminalId;
        this.locale = locale;
        this.baseUrl = baseUrl;
        this.privateKey = UpcSignature.loadPrivateKey(privateKeyPem);
        this.gatewayPublicKey = UpcSignature.loadPublicKey(gatewayPublicKeyPem);
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.RAIFFEISEN;
    }

    /**
     * The gateway expects a form POST, so we send the buyer to our own redirect page
     * (which auto-submits the signed form). The returned sessionId ({@code OrderID})
     * is stored as the payment's provider session id for correlating the callback.
     */
    @Override
    public CheckoutSession createSession(Order order, String successUrl, String cancelUrl) {
        String orderRef = orderRef(order);
        String checkoutUrl = baseUrl + REDIRECT_PATH + orderRef;
        return new CheckoutSession(orderRef, checkoutUrl, null);
    }

    /** Auto-submitting HTML form that POSTs the signed request fields to the bank. */
    public String renderRedirectForm(Order order) {
        Map<String, String> fields = buildRequestFields(order);
        StringBuilder inputs = new StringBuilder();
        fields.forEach((k, v) -> inputs.append("  <input type=\"hidden\" name=\"")
                .append(htmlEscape(k)).append("\" value=\"").append(htmlEscape(v)).append("\">\n"));
        return "<!doctype html>\n<html><head><meta charset=\"utf-8\"><title>Redirecting…</title></head>\n"
                + "<body onload=\"document.forms[0].submit()\">\n"
                + "<form method=\"post\" action=\"" + htmlEscape(gatewayUrl) + "\">\n"
                + inputs
                + "  <noscript><button type=\"submit\">Continue to payment</button></noscript>\n"
                + "</form></body></html>";
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
        PaymentEvent.Type type = isSuccessful(rawBody) ? PaymentEvent.Type.PAID : PaymentEvent.Type.FAILED;
        String eventId = "rba_" + orderRef + "_" + (xid != null ? xid : p.get("TranCode"));
        return new PaymentEvent(eventId, type, orderRef, xid, p.get("Rrn"), null);
    }

    /** §8: TranCode 000 = successful authorization. */
    public boolean isSuccessful(String rawBody) {
        return "000".equals(parseForm(rawBody).get("TranCode"));
    }

    /**
     * NOTIFY_URL handshake body (§7): the shop must answer with {@code Response.action}
     * or the gateway auto-reverses the transaction. Echoes the identifying params and
     * approves a successful auth, otherwise reverses.
     */
    public String buildNotifyResponse(String rawBody, boolean approve) {
        Map<String, String> p = parseForm(rawBody);
        StringBuilder sb = new StringBuilder();
        for (String key : new String[] {"TerminalID", "OrderID", "Currency", "TotalAmount", "XID", "PurchaseTime"}) {
            sb.append(key).append('=').append(nz(p.get(key))).append('\n');
        }
        sb.append("Response.action=").append(approve ? "approve" : "reverse").append('\n');
        sb.append("Response.reason=\n");
        sb.append("Response.forwardUrl=\n");
        return sb.toString();
    }

    // ---- request signing ---------------------------------------------------

    private Map<String, String> buildRequestFields(Order order) {
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
        return fields;
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

    private static String htmlEscape(String v) {
        return nz(v).replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
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
