package com.passlify.core.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Payten / NestPay "ver3" request+response hash used by Raiffeisen Serbia's 3D hosted
 * page. Parameters (excluding {@code hash} and {@code encoding}) are sorted by name
 * case-insensitively, their values pipe-joined with {@code \}/{@code |} escaped, the
 * store key appended, then SHA-512 + Base64.
 *
 * <p>NOTE: the exact excluded-field set and hash version must be confirmed against the
 * Raiffeisen merchant integration document; ver3 is the current NestPay default.
 */
public final class NestPayHash {

    private static final Set<String> EXCLUDED = Set.of("hash", "encoding");

    private NestPayHash() {
    }

    public static String compute(Map<String, String> params, String storeKey) {
        String joined = params.entrySet().stream()
                .filter(e -> !EXCLUDED.contains(e.getKey().toLowerCase()))
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(String::toLowerCase)))
                .map(e -> escape(e.getValue() == null ? "" : e.getValue()))
                .collect(Collectors.joining("|"));
        joined = joined + "|" + escape(storeKey);
        return Base64.getEncoder().encodeToString(sha512(joined));
    }

    /** Constant-time comparison of a presented hash against the recomputed one. */
    public static boolean matches(Map<String, String> params, String storeKey, String presentedHash) {
        if (presentedHash == null) {
            return false;
        }
        byte[] expected = compute(params, storeKey).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, presentedHash.getBytes(StandardCharsets.UTF_8));
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("|", "\\|");
    }

    private static byte[] sha512(String data) {
        try {
            return MessageDigest.getInstance("SHA-512").digest(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-512 unavailable", e);
        }
    }
}
