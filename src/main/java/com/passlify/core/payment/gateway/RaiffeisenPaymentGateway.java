package com.passlify.core.payment.gateway;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import com.passlify.core.order.Order;
import com.passlify.core.payment.PaymentProvider;
import java.math.BigDecimal;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Raiffeisen Serbia card payments via the Payten / NestPay <b>3D_PAY_HOSTED</b> model
 * (EVENT_DOMAIN_SPEC §10). Activated only when {@code passlify.raiffeisen.enabled=true}
 * and a store key is configured, so the app runs without it in dev/test.
 *
 * <p>{@link #createSession} builds the hash-signed hosted-page parameters; the browser
 * is sent to the bank's {@code est3Dgate}. The bank posts the result back to our okUrl/
 * failUrl (routed to {@code /api/v1/webhooks/raiffeisen}); {@link #verifyAndParse}
 * re-computes the response hash and maps the outcome.
 *
 * <p><b>Scope note:</b> the exact NestPay field set, hash version, and result codes must
 * be confirmed against the Raiffeisen merchant integration document before go-live;
 * refund/void notifications (server-to-server) are handled separately, not here.
 */
@Component
@ConditionalOnProperty(prefix = "passlify.raiffeisen", name = "enabled", havingValue = "true")
public class RaiffeisenPaymentGateway implements PaymentGateway {

    /** ISO-4217 numeric currency codes NestPay expects. */
    private static final Map<String, String> CURRENCY_CODES =
            Map.of("RSD", "941", "EUR", "978", "USD", "840", "GBP", "826");

    private final SecureRandom random = new SecureRandom();
    private final String gatewayUrl;
    private final String clientId;
    private final String storeKey;
    private final String okUrl;
    private final String failUrl;

    public RaiffeisenPaymentGateway(
            @Value("${passlify.raiffeisen.gateway-url}") String gatewayUrl,
            @Value("${passlify.raiffeisen.client-id}") String clientId,
            @Value("${passlify.raiffeisen.store-key}") String storeKey,
            @Value("${passlify.raiffeisen.ok-url}") String okUrl,
            @Value("${passlify.raiffeisen.fail-url}") String failUrl) {
        this.gatewayUrl = gatewayUrl;
        this.clientId = clientId;
        this.storeKey = storeKey;
        this.okUrl = okUrl;
        this.failUrl = failUrl;
    }

    @Override
    public PaymentProvider provider() {
        return PaymentProvider.RAIFFEISEN;
    }

    @Override
    public CheckoutSession createSession(Order order, String successUrl, String cancelUrl) {
        String oid = order.getId().toString();
        Map<String, String> params = new LinkedHashMap<>();
        params.put("clientid", clientId);
        params.put("oid", oid);
        params.put("amount", BigDecimal.valueOf(order.getTotalMinor(), 2).toPlainString());
        params.put("currency", currencyCode(order.getCurrency()));
        params.put("okUrl", okUrl);
        params.put("failUrl", failUrl);
        params.put("storetype", "3d_pay_hosted");
        params.put("trantype", "Auth");
        params.put("hashAlgorithm", "ver3");
        params.put("rnd", HexFormat.of().formatHex(randomBytes()));
        params.put("encoding", "UTF-8");
        params.put("hash", NestPayHash.compute(params, storeKey));

        String checkoutUrl = gatewayUrl + "?" + urlEncode(params);
        return new CheckoutSession(oid, checkoutUrl, null);
    }

    @Override
    public PaymentEvent verifyAndParse(String rawBody, String signature) {
        Map<String, String> params = parseForm(rawBody);
        String presentedHash = params.getOrDefault("HASH", params.get("hash"));
        if (!NestPayHash.matches(params, storeKey, presentedHash)) {
            throw ApiException.of(ErrorCode.BAD_SIGNATURE, "Invalid Raiffeisen callback hash");
        }
        String oid = params.get("oid");
        String transId = params.get("TransId");
        boolean approved = "Approved".equalsIgnoreCase(params.get("Response"))
                || "00".equals(params.get("ProcReturnCode"));
        PaymentEvent.Type type = approved ? PaymentEvent.Type.PAID : PaymentEvent.Type.FAILED;
        String eventId = "rba_" + oid + "_" + (transId != null ? transId : type);
        return new PaymentEvent(eventId, type, oid, transId, null, null);
    }

    private String currencyCode(String currency) {
        String code = CURRENCY_CODES.get(currency == null ? "" : currency.toUpperCase(Locale.ROOT));
        if (code == null) {
            throw ApiException.invalidState("Raiffeisen does not support currency " + currency);
        }
        return code;
    }

    private byte[] randomBytes() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        return b;
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
            String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            map.put(k, v);
        }
        return map;
    }
}
