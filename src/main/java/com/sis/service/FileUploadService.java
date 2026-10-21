package com.sis.service;

import com.sis.dto.AdminFileListingDto;
import com.sis.dto.AdminStoredFileDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * File Upload Service - Handles file operations for profile images
 * Manages storage, validation, and retrieval of uploaded files
 */
@Service
public class FileUploadService {

    private static final Logger logger = Logger.getLogger(FileUploadService.class.getName());

    @Value("${file.upload.directory}")
    private String uploadDir;

    @Value("${file.upload.max-size}")
    private long maxFileSize;

    private static final String[] ALLOWED_EXTENSIONS = {"jpg", "jpeg", "png", "gif"};
    private static final String STUDENT_DIR = "students";
    private static final String TEACHER_DIR = "teachers";
    private static final int MAX_LISTED_FILES = 500;

    public String uploadStudentProfileImage(MultipartFile file, Long studentId) throws IOException {
        return uploadProfileImage(file, STUDENT_DIR, studentId);
    }

    public String uploadTeacherProfileImage(MultipartFile file, Long teacherId) throws IOException {
        return uploadProfileImage(file, TEACHER_DIR, teacherId);
    }

    public String uploadStudentDocument(MultipartFile file, Long studentId) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is empty");
        if (file.getSize() > maxFileSize) throw new IllegalArgumentException("File size exceeds maximum limit");

        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        String extension = originalFilename == null ? "" : getFileExtension(originalFilename).toLowerCase();
        boolean isAllowedDocument = isAllowedDocumentType(originalFilename, contentType);

        if (!isAllowedDocument) {
            throw new IllegalArgumentException("Only PDF and image documents are allowed");
        }

        Path directory = resolveUploadRoot().resolve("documents").resolve(String.valueOf(studentId)).normalize();
        if (!Files.exists(directory)) Files.createDirectories(directory);

        String filename = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        Path target = directory.resolve(filename);
        file.transferTo(target.toFile());
        return "documents/" + studentId + "/" + filename;
    }

    public String uploadThesisDocument(MultipartFile file, Long studentId) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is empty");
        if (file.getSize() > maxFileSize) throw new IllegalArgumentException("File size exceeds maximum limit");
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !getFileExtension(originalFilename).equalsIgnoreCase("pdf")) {
            throw new IllegalArgumentException("Thesis submissions must be PDF files");
        }
        try (var input = file.getInputStream()) {
            byte[] signature = input.readNBytes(5);
            if (signature.length != 5 || signature[0] != '%' || signature[1] != 'P'
                    || signature[2] != 'D' || signature[3] != 'F' || signature[4] != '-') {
                throw new IllegalArgumentException("The uploaded file is not a valid PDF document");
            }
        }

        Path directory = resolveUploadRoot().resolve("research").resolve(String.valueOf(studentId)).normalize();
        if (!directory.startsWith(resolveUploadRoot())) throw new IllegalArgumentException("Invalid student identifier");
        Files.createDirectories(directory);
        String filename = UUID.randomUUID() + ".pdf";
        file.transferTo(directory.resolve(filename).toFile());
        return "research/" + studentId + "/" + filename;
    }

    public String uploadCourseMaterial(MultipartFile file, Long courseId) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is empty");
        if (file.getSize() > maxFileSize) throw new IllegalArgumentException("File size exceeds maximum limit");
        String originalFilename = file.getOriginalFilename();
        if (!isAllowedDocumentType(originalFilename, file.getContentType())) {
            throw new IllegalArgumentException("Only PDF and image documents are allowed");
        }
        String extension = getFileExtension(originalFilename).toLowerCase();
        Path directory = resolveUploadRoot().resolve("materials").resolve(String.valueOf(courseId)).normalize();
        if (!Files.exists(directory)) Files.createDirectories(directory);
        String filename = UUID.randomUUID() + (extension.isEmpty() ? "" : "." + extension);
        file.transferTo(directory.resolve(filename).toFile());
        return "materials/" + courseId + "/" + filename;
    }

    private boolean isAllowedDocumentType(String originalFilename, String contentType) {
        if (originalFilename == null || originalFilename.isBlank()) return false;
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (extension.equals("pdf") || extension.equals("png") || extension.equals("jpg") || extension.equals("jpeg") || extension.equals("gif")) {
            return true;
        }
        if (contentType == null) return false;
        String normalizedType = contentType.toLowerCase();
        return normalizedType.equals("application/pdf") || normalizedType.startsWith("image/");
    }

    private String uploadProfileImage(MultipartFile file, String directory, Long userId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds maximum limit: " + maxFileSize + " bytes");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !isAllowedFileType(originalFilename)) {
            throw new IllegalArgumentException("File type not allowed. Allowed types: jpg, jpeg, png, gif");
        }

        Path targetDirectory = resolveUploadRoot().resolve(directory).normalize();
        if (!Files.exists(targetDirectory)) {
            Files.createDirectories(targetDirectory);
            logger.info("Created directory: " + targetDirectory);
        }

        // Filename is always server-generated (never the client-supplied name), so
        // there's nothing here for a client to inject a path-traversal sequence into.
        String fileExtension = getFileExtension(originalFilename);
        String filename = userId + "_" + UUID.randomUUID() + "." + fileExtension;

        Path filepath = targetDirectory.resolve(filename);
        file.transferTo(filepath.toFile());
        logger.info("File uploaded successfully: " + filepath);

        return directory + "/" + filename;
    }

    private boolean isAllowedFileType(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (extension.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    private String getFileExtension(String filename) {
        int lastIndexOfDot = filename.lastIndexOf('.');
        return lastIndexOfDot > 0 ? filename.substring(lastIndexOfDot + 1) : "";
    }

    /**
     * Resolves a client-supplied relative path against the upload root and verifies
     * the result is still contained within that root.
     * <p>
     * Every public method here used to do {@code Paths.get(uploadDir, relativePath)}
     * with no validation at all, so a caller could pass {@code ../../../../etc/passwd}
     * (or a Windows equivalent) and read or delete arbitrary files on the host that the
     * application process has permission to touch - classic path traversal (CWE-22).
     * All access now funnels through this method so the containment check can't be
     * bypassed by adding a new file operation later.
     *
     * @throws SecurityException if the resolved path escapes the upload directory
     */
    private Path resolveSafePath(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Invalid file path");
        }
        Path root = resolveUploadRoot();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            logger.warning("Rejected path traversal attempt: " + relativePath);
            throw new SecurityException("Invalid file path");
        }
        return resolved;
    }

    private Path resolveUploadRoot() {
        return Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public String normalizeRelativePath(String relativePath) {
        Path root = resolveUploadRoot();
        return root.relativize(resolveSafePath(relativePath)).toString().replace('\\', '/');
    }

    public AdminFileListingDto listStoredFiles() throws IOException {
        Path root = resolveUploadRoot();
        if (!Files.isDirectory(root)) {
            return new AdminFileListingDto(List.of(), false);
        }

        List<AdminStoredFileDto> collectedFiles = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && !attributes.isSymbolicLink()) {
                    String relativePath = root.relativize(file).toString().replace('\\', '/');
                    collectedFiles.add(new AdminStoredFileDto(
                            relativePath, attributes.size(), attributes.lastModifiedTime().toInstant()));
                    if (collectedFiles.size() > MAX_LISTED_FILES) {
                        return FileVisitResult.TERMINATE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        List<AdminStoredFileDto> files = new ArrayList<>(collectedFiles);
        files.sort(Comparator.comparing(AdminStoredFileDto::getPath));
        boolean truncated = files.size() > MAX_LISTED_FILES;
        if (truncated) {
            files = new ArrayList<>(files.subList(0, MAX_LISTED_FILES));
        }
        return new AdminFileListingDto(files, truncated);
    }

    public boolean deleteProfileImage(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }
        try {
            Path filepath = resolveSafePath(relativePath);
            if (Files.exists(filepath)) {
                Files.delete(filepath);
                logger.info("File deleted successfully: " + filepath);
                return true;
            }
        } catch (IOException e) {
            logger.severe("Error deleting file: " + e.getMessage());
        } catch (SecurityException e) {
            return false;
        }
        return false;
    }

    public String getAbsoluteFilePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        return resolveSafePath(relativePath).toString();
    }

    public boolean fileExists(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }
        try {
            return Files.exists(resolveSafePath(relativePath));
        } catch (SecurityException e) {
            return false;
        }
    }

    public byte[] getFileAsBytes(String relativePath) throws IOException {
        Path filepath = resolveSafePath(relativePath);
        if (!Files.exists(filepath)) {
            throw new IOException("File not found: " + filepath);
        }
        return Files.readAllBytes(filepath);
    }

    public long getFileSize(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return 0;
        }
        try {
            Path filepath = resolveSafePath(relativePath);
            if (Files.exists(filepath)) {
                return Files.size(filepath);
            }
        } catch (IOException e) {
            logger.severe("Error getting file size: " + e.getMessage());
        } catch (SecurityException e) {
            return 0;
        }
        return 0;
    }
}
