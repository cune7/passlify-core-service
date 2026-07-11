package com.passlify.core.event;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Generates a ULID — a 26-character, lexicographically sortable, collision-resistant
 * public identifier (EVENT_DOMAIN_SPEC §5.2). 48-bit millisecond timestamp + 80 bits
 * of randomness, encoded in Crockford base32. Server-generated and immutable once set.
 *
 * <p>Used for the event {@code public_id} exposed in public URLs, QR/ticket
 * correlation, support cases, and integrations — never the sequential DB key.
 */
@Component
public class PublicIdGenerator {

    /** Crockford base32 alphabet (excludes I, L, O, U to avoid ambiguity). */
    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final int TIME_LEN = 10;
    private static final int RANDOM_LEN = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** @return a fresh 26-character ULID. */
    public String newId() {
        return generate();
    }

    /** Package-visible for deterministic testing. */
    String newId(long epochMilli) {
        return generate(epochMilli);
    }

    /** Static factory so entity {@code @PrePersist} callbacks can generate without DI. */
    public static String generate() {
        return generate(System.currentTimeMillis());
    }

    static String generate(long epochMilli) {
        char[] out = new char[TIME_LEN + RANDOM_LEN];
        long ts = epochMilli;
        for (int i = TIME_LEN - 1; i >= 0; i--) {
            out[i] = ENCODING[(int) (ts & 0x1f)];
            ts >>>= 5;
        }
        for (int i = TIME_LEN; i < out.length; i++) {
            out[i] = ENCODING[RANDOM.nextInt(ENCODING.length)];
        }
        return new String(out);
    }
}
