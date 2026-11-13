package com.sis.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sis.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system")
@PreAuthorize("@permissionSecurity.canManagePortalSettings()")
public class AdminSystemStatusController {
    private static final Logger logger = LoggerFactory.getLogger(AdminSystemStatusController.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final boolean mailEnabled;
    private final EmailService emailService;
    private final String backupStatusFile;
    private final String uploadBackupStatusFile;
    private final long backupMaxAgeHours;

    public AdminSystemStatusController(JdbcTemplate jdbcTemplate,
                                       ObjectMapper objectMapper,
                                       @Value("${app.mail.enabled:false}") boolean mailEnabled,
                                       EmailService emailService,
                                       @Value("${app.backup.status-file:}") String backupStatusFile,
                                       @Value("${app.backup.upload-status-file:}") String uploadBackupStatusFile,
                                       @Value("${app.backup.max-age-hours:36}") long backupMaxAgeHours) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.mailEnabled = mailEnabled;
        this.emailService = emailService;
        this.backupStatusFile = backupStatusFile;
        this.uploadBackupStatusFile = uploadBackupStatusFile;
        this.backupMaxAgeHours = backupMaxAgeHours;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("database", databaseStatus());
        result.put("email", emailStatus());
        result.put("backup", backupStatus());
        result.put("uploadBackup", backupStatus(uploadBackupStatusFile, "Upload backup"));
        result.put("checkedAt", Instant.now().toString());
        return result;
    }

    private Map<String, Object> backupStatus() {
        return backupStatus(backupStatusFile, "Database backup");
    }

    private Map<String, Object> backupStatus(String statusFile, String backupType) {
        if (!StringUtils.hasText(statusFile)) {
            return component("not_configured", "Not configured",
                    "No " + backupType.toLowerCase() + " status file is configured; completion and age cannot be verified.");
        }
        try {
            JsonNode marker = objectMapper.readTree(Files.readString(Path.of(statusFile)));
            String timestamp = marker.path("completedAt").asText("");
            String artifact = marker.path("artifact").asText("");
            String expectedChecksum = marker.path("sha256").asText("");
            if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(artifact)
                    || !expectedChecksum.matches("[A-Fa-f0-9]{64}")) {
                return component("unavailable", "Unavailable",
                        "Backup marker is invalid or does not identify its artifact and checksum.");
            }
            Path artifactPath = Path.of(artifact);
            Path checksumPath = Path.of(artifact + ".sha256");
            if (!Files.isRegularFile(artifactPath) || !Files.isRegularFile(checksumPath)) {
                return component("unavailable", "Unavailable",
                        "The backup artifact or its matching checksum is missing.");
            }
            String sidecarChecksum = Files.readString(checksumPath).trim();
            if (!expectedChecksum.equalsIgnoreCase(sidecarChecksum)
                    || !expectedChecksum.equalsIgnoreCase(sha256(artifactPath))) {
                return component("unavailable", "Unavailable",
                        "The backup artifact does not match its recorded SHA-256 checksum.");
            }

            Instant completedAt = Instant.parse(timestamp);
            Duration age = Duration.between(completedAt, Instant.now());
            if (age.isNegative()) {
                return component("unavailable", "Unavailable", backupType + " timestamp is in the future.");
            }
            if (age.compareTo(Duration.ofHours(backupMaxAgeHours)) > 0) {
                return component("stale", "Stale",
                        "Last successful " + backupType.toLowerCase() + " was " + age.toHours() + " hours ago.");
            }
            return component("online", "Recent",
                    "Last successful " + backupType.toLowerCase() + " completed at " + completedAt + ".");
        } catch (IOException | DateTimeParseException | InvalidPathException | SecurityException ex) {
            logger.warn("Configured {} status could not be read or parsed", backupType, ex);
            return component("unavailable", "Unavailable",
                    "No readable successful " + backupType.toLowerCase() + " timestamp is available.");
        }
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private Map<String, Object> databaseStatus() {
        try {
            Integer probeResult = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (Integer.valueOf(1).equals(probeResult)) {
                return component("online", "Connected", "Database connectivity probe succeeded.");
            }
            return component("unavailable", "Unavailable", "Database connectivity probe returned an unexpected result.");
        } catch (DataAccessException ex) {
            logger.warn("Admin database connectivity probe failed", ex);
            return component("unavailable", "Unavailable", "Database connectivity probe failed.");
        }
    }

    private Map<String, Object> emailStatus() {
        if (!mailEnabled) {
            return component("disabled", "Disabled", "Email delivery is disabled by configuration.");
        }
        if (!emailService.isDeliveryConfigured()) {
            return component("incomplete", "Incomplete", "Email delivery is enabled but provider settings are incomplete.");
        }
        java.time.Instant testSentAt = emailService.getLastSuccessfulTestEmailAt();
        if (testSentAt != null) {
            return component("online", "Test sent",
                    "The latest email test was accepted by the configured provider at " + testSentAt + ".");
        }
        return component("online", "Configured; ready",
                "Email provider settings are present and the mail service is ready to send messages.");
    }

    private Map<String, Object> component(String status, String label, String detail) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("status", status);
        value.put("label", label);
        value.put("detail", detail);
        return value;
    }
}
