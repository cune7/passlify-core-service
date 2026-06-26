package com.passlify.core.issuance.qr;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Signs and verifies QR tokens with HMAC-SHA256 (DOMAIN §4.6) — the legacy system
 * used a plain, forgeable payload. Token = {@code base64url(payloadJson).base64url(hmac)}.
 * A valid signature proves we issued the ticket; the DB lookup proves it's usable.
 */
@Service
public class QrTokenService {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final byte[] secret;

    public QrTokenService(ObjectMapper objectMapper, @Value("${passlify.qr-secret}") String secret) {
        this.objectMapper = objectMapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    /** Compact signed token to encode into the QR image. */
    public String sign(UUID ticketId, UUID eventId, String serialNumber) {
        byte[] payload = objectMapper.writeValueAsBytes(
                new Payload(ticketId.toString(), eventId.toString(), serialNumber));
        String payloadB64 = ENCODER.encodeToString(payload);
        String sigB64 = ENCODER.encodeToString(hmac(payloadB64.getBytes(StandardCharsets.UTF_8)));
        return payloadB64 + "." + sigB64;
    }

    /** Verifies the signature and returns the decoded payload, or throws BAD_SIGNATURE. */
    public VerifiedToken verify(String token) {
        if (token == null) {
            throw badSignature();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            throw badSignature();
        }
        String payloadB64 = token.substring(0, dot);
        byte[] presentedSig;
        try {
            presentedSig = DECODER.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw badSignature();
        }
        byte[] expectedSig = hmac(payloadB64.getBytes(StandardCharsets.UTF_8));
        if (!MessageDigest.isEqual(expectedSig, presentedSig)) {
            throw badSignature();
        }
        try {
            Payload p = objectMapper.readValue(DECODER.decode(payloadB64), Payload.class);
            return new VerifiedToken(UUID.fromString(p.tid()), UUID.fromString(p.ev()), p.sn());
        } catch (RuntimeException e) {
            throw badSignature();
        }
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret, HMAC_ALG));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    private static ApiException badSignature() {
        return ApiException.of(ErrorCode.BAD_SIGNATURE, "Invalid QR token signature");
    }

    /** Decoded, verified token contents. */
    public record VerifiedToken(UUID ticketId, UUID eventId, String serialNumber) {
    }

    /** Wire payload (short keys keep the QR small). */
    record Payload(String tid, String ev, String sn) {
    }
}
