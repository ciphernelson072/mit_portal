package com.sis.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    /**
     * Upload profile image for student
     * Saves file to C:/uploads/students/ directory
     * @param file the file to upload
     * @param studentId student identifier
     * @return file path relative to upload directory
     * @throws IOException if upload fails
     * @throws IllegalArgumentException if file is invalid
     */
    public String uploadStudentProfileImage(MultipartFile file, Long studentId) throws IOException {
        return uploadProfileImage(file, STUDENT_DIR, studentId);
    }

    /**
     * Upload profile image for teacher
     * Saves file to C:/uploads/teachers/ directory
     * @param file the file to upload
     * @param teacherId teacher identifier
     * @return file path relative to upload directory
     * @throws IOException if upload fails
     * @throws IllegalArgumentException if file is invalid
     */
    public String uploadTeacherProfileImage(MultipartFile file, Long teacherId) throws IOException {
        return uploadProfileImage(file, TEACHER_DIR, teacherId);
    }

    /**
     * Internal method to handle profile image upload
     * @param file the file to upload
     * @param directory subdirectory (students or teachers)
     * @param userId user identifier
     * @return file path relative to upload directory
     * @throws IOException if upload fails
     * @throws IllegalArgumentException if file is invalid
     */
    private String uploadProfileImage(MultipartFile file, String directory, Long userId) throws IOException {
        // Validate file
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        // Check file size
        if (file.getSize() > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds maximum limit: " + maxFileSize + " bytes");
        }

        // Check file type
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !isAllowedFileType(originalFilename)) {
            throw new IllegalArgumentException("File type not allowed. Allowed types: jpg, jpeg, png, gif");
        }

        // Create directory if not exists
        Path targetDirectory = Paths.get(uploadDir, directory);
        if (!Files.exists(targetDirectory)) {
            Files.createDirectories(targetDirectory);
            logger.info("Created directory: " + targetDirectory);
        }

        // Generate unique filename
        String fileExtension = getFileExtension(originalFilename);
        String filename = userId + "_" + UUID.randomUUID().toString() + "." + fileExtension;

        // Save file
        Path filepath = targetDirectory.resolve(filename);
        file.transferTo(filepath.toFile());
        logger.info("File uploaded successfully: " + filepath);

        // Return relative path for database storage
        return directory + "/" + filename;
    }

    /**
     * Check if file extension is allowed
     * @param filename the filename to check
     * @return true if extension is allowed
     */
    private boolean isAllowedFileType(String filename) {
        String extension = getFileExtension(filename).toLowerCase();
        for (String allowed : ALLOWED_EXTENSIONS) {
            if (extension.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get file extension from filename
     * @param filename the filename
     * @return file extension without dot
     */
    private String getFileExtension(String filename) {
        int lastIndexOfDot = filename.lastIndexOf('.');
        return lastIndexOfDot > 0 ? filename.substring(lastIndexOfDot + 1) : "";
    }

    /**
     * Delete profile image file
     * @param relativePath relative path from upload directory
     * @return true if deletion successful
     */
    public boolean deleteProfileImage(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }

        try {
            Path filepath = Paths.get(uploadDir, relativePath);
            if (Files.exists(filepath)) {
                Files.delete(filepath);
                logger.info("File deleted successfully: " + filepath);
                return true;
            }
        } catch (IOException e) {
            logger.severe("Error deleting file: " + e.getMessage());
        }

        return false;
    }

    /**
     * Get absolute file path from relative path
     * @param relativePath relative path from upload directory
     * @return absolute file path
     */
    public String getAbsoluteFilePath(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return null;
        }
        return Paths.get(uploadDir, relativePath).toAbsolutePath().toString();
    }

    /**
     * Check if file exists
     * @param relativePath relative path from upload directory
     * @return true if file exists
     */
    public boolean fileExists(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return false;
        }
        return Files.exists(Paths.get(uploadDir, relativePath));
    }

    /**
     * Get file as byte array
     * @param relativePath relative path from upload directory
     * @return file contents as byte array
     * @throws IOException if file cannot be read
     */
    public byte[] getFileAsBytes(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isEmpty()) {
            throw new IllegalArgumentException("Invalid file path");
        }

        Path filepath = Paths.get(uploadDir, relativePath);
        if (!Files.exists(filepath)) {
            throw new IOException("File not found: " + filepath);
        }

        return Files.readAllBytes(filepath);
    }

    /**
     * Get file size in bytes
     * @param relativePath relative path from upload directory
     * @return file size in bytes, or 0 if not found
     */
    public long getFileSize(String relativePath) {
        if (relativePath == null || relativePath.isEmpty()) {
            return 0;
        }

        try {
            Path filepath = Paths.get(uploadDir, relativePath);
            if (Files.exists(filepath)) {
                return Files.size(filepath);
            }
        } catch (IOException e) {
            logger.severe("Error getting file size: " + e.getMessage());
        }

        return 0;
    }
}
