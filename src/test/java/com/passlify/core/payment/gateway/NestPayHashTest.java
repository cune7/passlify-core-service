package com.passlify.core.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class NestPayHashTest {

    private static final String STORE_KEY = "test-store-key";

    @Test
    void hashIsDeterministicAndOrderIndependent() {
        Map<String, String> a = new LinkedHashMap<>();
        a.put("clientid", "100000");
        a.put("oid", "order-1");
        a.put("amount", "1500.00");

        Map<String, String> reordered = new TreeMap<>(a); // different iteration order
        assertThat(NestPayHash.compute(a, STORE_KEY)).isEqualTo(NestPayHash.compute(reordered, STORE_KEY));
    }

    @Test
    void matchesAcceptsTheComputedHashAndRejectsTampering() {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("oid", "order-1");
        params.put("Response", "Approved");
        params.put("ProcReturnCode", "00");
        String hash = NestPayHash.compute(params, STORE_KEY);

        assertThat(NestPayHash.matches(params, STORE_KEY, hash)).isTrue();
        assertThat(NestPayHash.matches(params, STORE_KEY, hash + "x")).isFalse();
        assertThat(NestPayHash.matches(params, "wrong-key", hash)).isFalse();
        assertThat(NestPayHash.matches(params, STORE_KEY, null)).isFalse();
    }

    @Test
    void hashAndEncodingFieldsAreExcluded() {
        Map<String, String> withoutHash = new LinkedHashMap<>();
        withoutHash.put("oid", "order-1");
        String expected = NestPayHash.compute(withoutHash, STORE_KEY);

        Map<String, String> withHash = new LinkedHashMap<>(withoutHash);
        withHash.put("hash", "should-be-ignored");
        withHash.put("encoding", "UTF-8");
        assertThat(NestPayHash.compute(withHash, STORE_KEY)).isEqualTo(expected);
    }
}
