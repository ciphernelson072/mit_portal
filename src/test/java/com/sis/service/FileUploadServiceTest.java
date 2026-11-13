package com.sis.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileUploadServiceTest {

    @TempDir
    Path uploadRoot;

    @Test
    void thesisUploadStoresPdfWithGeneratedNameUnderStudentDirectory() throws Exception {
        FileUploadService service = service(1024);
        byte[] pdf = "%PDF-1.7 test".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "final-thesis.pdf", "application/pdf", pdf);

        String path = service.uploadThesisDocument(file, 17L);

        org.junit.jupiter.api.Assertions.assertTrue(path.matches("research/17/[0-9a-f-]+\\.pdf"));
        assertArrayEquals(pdf, Files.readAllBytes(uploadRoot.resolve(path)));
    }

    @Test
    void thesisUploadRejectsNonPdfAndForgedPdfExtensions() {
        FileUploadService service = service(1024);
        MockMultipartFile image = new MockMultipartFile("file", "thesis.png", "image/png", new byte[]{1, 2, 3});
        MockMultipartFile forged = new MockMultipartFile("file", "thesis.pdf", "application/pdf", "not a pdf".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.uploadThesisDocument(image, 17L));
        assertThrows(IllegalArgumentException.class, () -> service.uploadThesisDocument(forged, 17L));
    }

    @Test
    void thesisUploadEnforcesConfiguredSizeLimit() {
        FileUploadService service = service(4);
        MockMultipartFile file = new MockMultipartFile("file", "thesis.pdf", "application/pdf", "%PDF-1.7".getBytes());

        assertThrows(IllegalArgumentException.class, () -> service.uploadThesisDocument(file, 17L));
    }

    @Test
    void fileListingReturnsOnlyRegularFilesAsUploadRelativePaths() throws Exception {
        FileUploadService service = service(1024);
        Path file = uploadRoot.resolve("documents").resolve("17").resolve("test.pdf");
        Files.createDirectories(file.getParent());
        Files.write(file, "%PDF-1.7".getBytes());

        var listing = service.listStoredFiles();

        assertEquals(1, listing.getFiles().size());
        assertEquals("documents/17/test.pdf", listing.getFiles().get(0).getPath());
        assertEquals(Files.size(file), listing.getFiles().get(0).getSizeBytes());
        assertFalse(listing.isTruncated());
    }

    @Test
    void fileListingCapsLargeDirectoriesAndReportsTruncation() throws Exception {
        FileUploadService service = service(1024);
        Path directory = uploadRoot.resolve("large");
        Files.createDirectories(directory);
        for (int index = 0; index < 501; index++) {
            Files.createFile(directory.resolve(String.format("%03d.txt", index)));
        }

        var listing = service.listStoredFiles();

        assertEquals(500, listing.getFiles().size());
        assertTrue(listing.isTruncated());
        assertTrue(listing.getFiles().stream().allMatch(file -> file.getPath().startsWith("large/")));
    }

    @Test
    void deleteRemovesAnUnreferencedFileInsideTheConfiguredUploadRoot() throws Exception {
        FileUploadService service = service(1024);
        Path file = uploadRoot.resolve("tmp").resolve("orphan.txt");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{1, 2, 3});

        assertTrue(service.deleteProfileImage("tmp/orphan.txt"));
        assertFalse(Files.exists(file));
    }

    private FileUploadService service(long maxFileSize) {
        FileUploadService service = new FileUploadService();
        ReflectionTestUtils.setField(service, "uploadDir", uploadRoot.toString());
        ReflectionTestUtils.setField(service, "maxFileSize", maxFileSize);
        return service;
    }
}
