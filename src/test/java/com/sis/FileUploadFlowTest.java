package com.sis;

import com.sis.service.FileUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class FileUploadFlowTest {

    @Autowired
    private FileUploadService fileUploadService;

    @Test
    void studentDocumentUploadAcceptsPdfWithBrowserLikeOctetStreamMime() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "report.pdf",
                "application/octet-stream",
                "%PDF-1.4 test content".getBytes(StandardCharsets.UTF_8)
        );

        String path = fileUploadService.uploadStudentDocument(file, 42L);

        assertTrue(path.startsWith("documents/42/"));
        assertArrayEquals("%PDF-1.4 test content".getBytes(StandardCharsets.UTF_8),
                fileUploadService.getFileAsBytes(path));
    }
}
