package com.sis.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;

@Service
public class MfaQrCodeService {
    private static final int SIZE = 256;
    private static final String DATA_URI_PREFIX = "data:image/png;base64,";

    public String generateDataUri(String otpUri) {
        if (otpUri == null || !otpUri.startsWith("otpauth://totp/")) {
            throw new IllegalArgumentException("A valid TOTP provisioning URI is required.");
        }

        try {
            var hints = Map.of(
                    EncodeHintType.CHARACTER_SET, "UTF-8",
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, 2);
            var matrix = new QRCodeWriter().encode(
                    otpUri, BarcodeFormat.QR_CODE, SIZE, SIZE, hints);
            var image = MatrixToImageWriter.toBufferedImage(matrix);
            try (var output = new ByteArrayOutputStream()) {
                if (!ImageIO.write(image, "PNG", output)) {
                    throw new IllegalStateException("PNG encoding is unavailable for MFA QR generation.");
                }
                return DATA_URI_PREFIX + Base64.getEncoder().encodeToString(output.toByteArray());
            }
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Could not generate the MFA setup QR code.", ex);
        }
    }
}
