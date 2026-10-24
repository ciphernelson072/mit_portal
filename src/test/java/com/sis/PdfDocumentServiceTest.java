package com.sis;

import com.sis.service.PdfDocumentService;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfDocumentServiceTest {

    @Test
    void invoiceIsGeneratedAsPdf() throws Exception {
        byte[] document = new PdfDocumentService()
                .invoice("INV-1", "Tuition", "2000000", "500000", "1500000",
                        "2026-12-15", "PARTIALLY PAID");

        assertTrue(document.length > 100);
        assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII), java.util.Arrays.copyOf(document, 4));
    }

    @Test
    void serviceRequestIsGeneratedAsPdf() throws Exception {
        byte[] document = new PdfDocumentService()
                .serviceRequest("REQ-1", "TRANSCRIPT", "Academic transcript", "Please issue my transcript.",
                        "COMPLETED", "2026-09-08");

        assertArrayEquals("%PDF".getBytes(StandardCharsets.US_ASCII), java.util.Arrays.copyOf(document, 4));
    }
}
