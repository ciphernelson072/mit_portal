package com.sis.service;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MfaQrCodeServiceTest {
    private final MfaQrCodeService service = new MfaQrCodeService();

    @Test
    void generatedQrCodeContainsTheExactProvisioningUri() throws Exception {
        String otpUri = "otpauth://totp/MIT%20Portal%3Aadmin?secret=JBSWY3DPEHPK3PXP&issuer=MIT%20Portal";
        String dataUri = service.generateDataUri(otpUri);

        assertEquals("data:image/png;base64,", dataUri.substring(0, "data:image/png;base64,".length()));
        byte[] imageBytes = Base64.getDecoder().decode(dataUri.substring("data:image/png;base64,".length()));
        var image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        assertNotNull(image);

        var result = new MultiFormatReader().decode(
                new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image))));
        assertEquals(otpUri, result.getText());
    }

    @Test
    void rejectsNonTotpProvisioningUris() {
        assertThrows(IllegalArgumentException.class, () -> service.generateDataUri("https://example.com"));
    }
}
