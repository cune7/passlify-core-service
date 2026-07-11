package com.passlify.core.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class UpcSignatureTest {

    private final KeyPair merchant = generate();
    private final KeyPair other = generate();

    @Test
    void signVerifyRoundTrips() {
        String data = "1753019;E7881019;190101120000;19;941;242;;";
        String sig = UpcSignature.sign(data, merchant.getPrivate());
        assertThat(UpcSignature.verify(data, sig, merchant.getPublic())).isTrue();
    }

    @Test
    void rejectsTamperedDataWrongKeyAndNull() {
        String data = "a;b;c;";
        String sig = UpcSignature.sign(data, merchant.getPrivate());
        assertThat(UpcSignature.verify("a;b;X;", sig, merchant.getPublic())).isFalse();
        assertThat(UpcSignature.verify(data, sig, other.getPublic())).isFalse();
        assertThat(UpcSignature.verify(data, null, merchant.getPublic())).isFalse();
    }

    @Test
    void loadsKeysFromPem() {
        String privPem = pem("PRIVATE KEY", merchant.getPrivate().getEncoded());
        String pubPem = pem("PUBLIC KEY", merchant.getPublic().getEncoded());
        String data = "x;y;";
        String sig = UpcSignature.sign(data, UpcSignature.loadPrivateKey(privPem));
        assertThat(UpcSignature.verify(data, sig, UpcSignature.loadPublicKey(pubPem))).isTrue();
    }

    static String pem(String type, byte[] der) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getEncoder().encodeToString(der) + "\n-----END " + type + "-----\n";
    }

    static KeyPair generate() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
