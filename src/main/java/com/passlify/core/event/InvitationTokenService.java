package com.passlify.core.event;

import com.passlify.core.common.error.ApiException;
import com.passlify.core.common.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Signs and verifies collaborator invitation tokens with HMAC-SHA256 (EVENT_DOMAIN_SPEC
 * §38) — the collaborator id alone is guessable, so acceptance requires a signed,
 * expiring token. Token = {@code base64url("<collaboratorId>:<expiresEpochMillis>").base64url(hmac)}.
 * The signature proves we issued the invite; expiry is enforced by the caller against
 * the persisted invitation.
 */
@Service
public class InvitationTokenService {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private final byte[] secret;

    public InvitationTokenService(@Value("${passlify.invite-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(UUID collaboratorId, Instant expiresAt) {
        String payload = collaboratorId + ":" + expiresAt.toEpochMilli();
        String payloadB64 = ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String sigB64 = ENCODER.encodeToString(hmac(payloadB64.getBytes(StandardCharsets.UTF_8)));
        return payloadB64 + "." + sigB64;
    }

    /** Verifies the signature and returns the decoded contents, or throws BAD_SIGNATURE. */
    public VerifiedInvite verify(String token) {
        if (token == null) {
            throw badToken();
        }
        int dot = token.indexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            throw badToken();
        }
        String payloadB64 = token.substring(0, dot);
        byte[] presentedSig;
        try {
            presentedSig = DECODER.decode(token.substring(dot + 1));
        } catch (IllegalArgumentException e) {
            throw badToken();
        }
        if (!MessageDigest.isEqual(hmac(payloadB64.getBytes(StandardCharsets.UTF_8)), presentedSig)) {
            throw badToken();
        }
        try {
            String payload = new String(DECODER.decode(payloadB64), StandardCharsets.UTF_8);
            int sep = payload.lastIndexOf(':');
            UUID collaboratorId = UUID.fromString(payload.substring(0, sep));
            Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(payload.substring(sep + 1)));
            return new VerifiedInvite(collaboratorId, expiresAt);
        } catch (RuntimeException e) {
            throw badToken();
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

    private static ApiException badToken() {
        return ApiException.of(ErrorCode.BAD_SIGNATURE, "Invalid or malformed invitation token");
    }

    public record VerifiedInvite(UUID collaboratorId, Instant expiresAt) {
    }
}
