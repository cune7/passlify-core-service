package com.passlify.core.issuance.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import org.springframework.stereotype.Service;

/** Renders a QR PNG on demand from a token string (never stored as a blob, DOMAIN §2.9). */
@Service
public class QrImageService {

    private static final int DEFAULT_SIZE = 320;

    public byte[] pngFor(String token) {
        return pngFor(token, DEFAULT_SIZE);
    }

    public byte[] pngFor(String token, int size) {
        try {
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 1);
            BitMatrix matrix = new QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render QR image", e);
        }
    }
}
