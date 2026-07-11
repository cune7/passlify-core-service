package com.passlify.core.payment.gateway;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * RSA-SHA1 signing/verification for the UPC "e-Commerce Connect Gateway" used by
 * Raiffeisen Serbia (Shop Gateway Interface). The merchant's private key signs the
 * outgoing request datafile; the gateway's public key (from its server certificate)
 * verifies the response datafile. Matches the reference PHP {@code openssl_sign($data,
 * $sig, $key)} (default digest SHA-1) + {@code base64_encode}.
 */
public final class UpcSignature {

    private static final String SIG_ALG = "SHA1withRSA";

    private UpcSignature() {
    }

    /** @return base64 RSA-SHA1 signature of {@code data}. */
    public static String sign(String data, PrivateKey privateKey) {
        try {
            Signature signer = Signature.getInstance(SIG_ALG);
            signer.initSign(privateKey);
            signer.update(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign UPC request", e);
        }
    }

    public static boolean verify(String data, String base64Signature, PublicKey publicKey) {
        if (base64Signature == null) {
            return false;
        }
        try {
            Signature verifier = Signature.getInstance(SIG_ALG);
            verifier.initVerify(publicKey);
            verifier.update(data.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(Base64.getDecoder().decode(base64Signature.trim()));
        } catch (Exception e) {
            return false;
        }
    }

    /** Loads a PKCS#8 PEM ({@code BEGIN PRIVATE KEY}) RSA private key. */
    public static PrivateKey loadPrivateKey(String pem) {
        byte[] der = pemBody(pem);
        try {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Invalid RSA private key (expected PKCS#8 PEM; convert with 'openssl pkcs8 -topk8')", e);
        }
    }

    /** Loads a public key from either a {@code CERTIFICATE} or a {@code PUBLIC KEY} PEM. */
    public static PublicKey loadPublicKey(String pem) {
        try {
            if (pem.contains("CERTIFICATE")) {
                X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                        .generateCertificate(new java.io.ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8)));
                return cert.getPublicKey();
            }
            return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(pemBody(pem)));
        } catch (Exception e) {
            throw new IllegalStateException("Invalid gateway public key/certificate PEM", e);
        }
    }

    private static byte[] pemBody(String pem) {
        String base64 = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }
}
