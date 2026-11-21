package com.sis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sis.entity.NotificationCategory;
import com.sis.entity.Role;
import com.sis.entity.User;
import com.sis.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class BackupHealthMonitorService {
    private final NotificationService notifications;
    private final NotificationRecipientService recipients;
    private final ObjectMapper objectMapper;
    private final String databaseStatusFile;
    private final String uploadStatusFile;
    private final long maxAgeHours;
    private final String timeZone;
    private final OperationalMetricsService metrics;

    public BackupHealthMonitorService(
            NotificationService notifications,
            NotificationRecipientService recipients,
            ObjectMapper objectMapper,
            @Value("${app.backup.status-file:}") String databaseStatusFile,
            @Value("${app.backup.upload-status-file:}") String uploadStatusFile,
            @Value("${app.backup.max-age-hours:36}") long maxAgeHours,
            @Value("${app.automation.time-zone:Africa/Nairobi}") String timeZone) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.objectMapper = objectMapper;
        this.databaseStatusFile = databaseStatusFile;
        this.uploadStatusFile = uploadStatusFile;
        this.maxAgeHours = maxAgeHours;
        this.timeZone = timeZone;
        this.metrics = null;
    }

    @Autowired
    public BackupHealthMonitorService(
            NotificationService notifications,
            NotificationRecipientService recipients,
            ObjectMapper objectMapper,
            @Value("${app.backup.status-file:}") String databaseStatusFile,
            @Value("${app.backup.upload-status-file:}") String uploadStatusFile,
            @Value("${app.backup.max-age-hours:36}") long maxAgeHours,
            @Value("${app.automation.time-zone:Africa/Nairobi}") String timeZone,
            OperationalMetricsService metrics) {
        this.notifications = notifications;
        this.recipients = recipients;
        this.objectMapper = objectMapper;
        this.databaseStatusFile = databaseStatusFile;
        this.uploadStatusFile = uploadStatusFile;
        this.maxAgeHours = maxAgeHours;
        this.timeZone = timeZone;
        this.metrics = metrics;
    }

    public int monitor(LocalDate today) {
        int alerts = 0;
        alerts += monitorOne("database", databaseStatusFile, "Database backup",
                today, "PORTAL_SETTINGS_MANAGE");
        alerts += monitorOne("uploads", uploadStatusFile, "Upload backup",
                today, "PORTAL_SETTINGS_MANAGE");
        return alerts;
    }

    private int monitorOne(String kind, String statusFile, String label,
                           LocalDate today, String permission) {
        String status = checkStatus(statusFile);
        if ("online".equals(status)) {
            return 0;
        }
        if (metrics != null) {
            metrics.backupHealthAlert(status);
        }
        String title = label + " needs attention";
        String message = switch (status) {
            case "not_configured" -> label + " status is not configured. No successful backup evidence can be verified.";
            case "stale" -> label + " evidence is stale. The last checksum-verified backup exceeded "
                    + maxAgeHours + " hours.";
            case "future" -> label + " marker has a future timestamp and must be reviewed.";
            default -> label + " marker or checksum is unavailable or invalid.";
        };
        int created = 0;
        for (User recipient : recipients.findActiveUsersWithPermission(permission, Role.ADMIN)) {
            boolean added = notifications.createAutomated(
                    "backup-health:" + kind + ":" + today,
                    recipient.getId(), title, message, NotificationCategory.SYSTEM);
            if (added) created++;
        }
        return created;
    }

    String checkStatus(String statusFile) {
        if (statusFile == null || statusFile.isBlank()) {
            return "not_configured";
        }
        try {
            JsonNode marker = objectMapper.readTree(Files.readString(Path.of(statusFile)));
            String timestamp = marker.path("completedAt").asText("");
            String artifact = marker.path("artifact").asText("");
            String expectedChecksum = marker.path("sha256").asText("");
            if (!timestamp.isEmpty() && !artifact.isEmpty()
                    && expectedChecksum.matches("[A-Fa-f0-9]{64}")
                    && Files.isRegularFile(Path.of(artifact))
                    && Files.isRegularFile(Path.of(artifact + ".sha256"))
                    && expectedChecksum.equalsIgnoreCase(Files.readString(Path.of(artifact + ".sha256")).trim())
                    && expectedChecksum.equalsIgnoreCase(sha256(Path.of(artifact)))) {
                Instant completedAt = Instant.parse(timestamp);
                Duration age = Duration.between(completedAt, Instant.now());
                if (age.isNegative()) return "future";
                if (age.compareTo(Duration.ofHours(maxAgeHours)) > 0) return "stale";
                return "online";
            }
        } catch (IOException | java.time.format.DateTimeParseException
                 | SecurityException | IllegalArgumentException exception) {
            return "unavailable";
        }
        return "unavailable";
    }

    private String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(Files.readAllBytes(file))).toLowerCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
