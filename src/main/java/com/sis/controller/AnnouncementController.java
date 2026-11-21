package com.sis.controller;

import com.sis.dto.AnnouncementDto;
import com.sis.dto.CreateAnnouncementRequest;
import com.sis.entity.User;
import com.sis.entity.NotificationCategory;
import com.sis.exception.ResourceNotFoundException;
import com.sis.service.SchoolService;
import com.sis.repository.UserRepository;
import com.sis.service.NotificationService;
import com.sis.service.EmailService;
import com.sis.service.ProfileContactEmailService;
import com.sis.repository.StudentRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.CourseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import com.sis.entity.AnnouncementAudience;
import com.sis.entity.Role;

@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final SchoolService schoolService;
    private final NotificationService notifications;
    private final UserRepository users;
    private final ProfileContactEmailService contactEmails;
    @Autowired
    private EmailService emailService;
    @Autowired
    private StudentRepository students;
    @Autowired
    private StudentProgramRepository studentPrograms;
    @Autowired
    private CourseRepository courses;

    public AnnouncementController(SchoolService schoolService, NotificationService notifications, UserRepository users,
                                  ProfileContactEmailService contactEmails) {
        this.schoolService = schoolService;
        this.notifications = notifications;
        this.users = users;
        this.contactEmails = contactEmails;
    }

    @GetMapping
    public List<AnnouncementDto> listAnnouncements() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Role currentRole = schoolService.getUserByUsername(username)
                .map(User::getRole)
                .orElse(null);
        return schoolService.listAnnouncements().stream()
                .filter(announcement -> isVisibleTo(
                        announcement.getAudience(), announcement.getProgramIds(),
                        announcement.getCourseIds(), currentRole, username))
                .toList();
    }

    @PostMapping
    @PreAuthorize("@permissionSecurity.canManageAnnouncements()")
    public ResponseEntity<AnnouncementDto> createAnnouncement(@Valid @RequestBody CreateAnnouncementRequest request) {
        boolean hasTargets = (request.getProgramIds() != null && !request.getProgramIds().isEmpty())
                || (request.getCourseIds() != null && !request.getCourseIds().isEmpty());
        if (hasTargets && request.getAudience() != AnnouncementAudience.STUDENT) {
            throw new IllegalArgumentException("Programme and course targets require the STUDENT audience.");
        }
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User author = schoolService.getUserByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        AnnouncementDto ann;
        if ((request.getProgramIds() == null || request.getProgramIds().isEmpty())
                && (request.getCourseIds() == null || request.getCourseIds().isEmpty())) {
            ann = schoolService.createAnnouncement(
                    request.getTitle(), request.getContent(), author, request.getAudience());
        } else {
            ann = schoolService.createAnnouncement(
                    request.getTitle(), request.getContent(), author, request.getAudience(),
                    request.getProgramIds(), request.getCourseIds());
        }
        List<User> recipients = users.findActiveUserIdsExcluding(author.getId()).stream()
                .map(users::findById)
                .flatMap(java.util.Optional::stream)
                .filter(user -> user.getRole() != null && isVisibleToAudience(
                        request.getAudience(), user.getRole()))
                .filter(user -> isTargetRecipient(
                        user, request.getProgramIds(), request.getCourseIds()))
                .toList();
        recipients.forEach(user -> notifications.createInAppOnly(
                user.getId(), "New announcement", ann.getTitle(), NotificationCategory.ANNOUNCEMENT));
        ann.setInAppRecipientCount(recipients.size());
        var profileEmails = contactEmails.findForUsers(
                recipients.stream().map(User::getId).toList());
        int emailRecipientCount = 0;
        int emailSuccessCount = 0;
        int emailFailureCount = 0;
        int emailSkippedCount = 0;
        for (User user : recipients) {
            Long userId = user.getId();
            if (emailService == null || userId == null) {
                emailSkippedCount++;
                continue;
            }
            if (!notifications.emailNotificationsEnabled(userId)) {
                emailSkippedCount++;
                continue;
            }
            String email = user.getRole() == Role.ADMIN
                    ? user.getEmail()
                    : profileEmails.get(userId);
            if (email == null) {
                emailSkippedCount++;
                continue;
            }
            emailRecipientCount++;
            try {
                emailService.sendAnnouncementNotification(
                        email, user.getUsername(), ann.getTitle(), ann.getContent());
                emailSuccessCount++;
            } catch (IllegalArgumentException | IllegalStateException ex) {
                emailFailureCount++;
                org.slf4j.LoggerFactory.getLogger(AnnouncementController.class)
                        .warn("Announcement email was not delivered for user {}: {}",
                                userId, ex.getMessage());
            }
        }
        ann.setEmailRecipientCount(emailRecipientCount);
        ann.setEmailSuccessCount(emailSuccessCount);
        ann.setEmailFailureCount(emailFailureCount);
        ann.setEmailSkippedCount(emailSkippedCount);
        return ResponseEntity.status(HttpStatus.CREATED).body(ann);
    }

    private boolean isVisibleTo(String audience, List<Long> programIds, List<Long> courseIds,
                                Role role, String username) {
        if (audience == null) return true;
        try {
            if (!isVisibleToAudience(AnnouncementAudience.valueOf(audience), role)) {
                return false;
            }
            if ((programIds == null || programIds.isEmpty())
                    && (courseIds == null || courseIds.isEmpty())) {
                return true;
            }
            if (role != Role.STUDENT) return false;
            User user = schoolService.getUserByUsername(username).orElse(null);
            return user != null && isTargetRecipient(user, programIds, courseIds);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isTargetRecipient(User user, List<Long> programIds, List<Long> courseIds) {
        if (user == null || user.getRole() != Role.STUDENT) {
            return (programIds == null || programIds.isEmpty())
                    && (courseIds == null || courseIds.isEmpty());
        }
        if ((programIds == null || programIds.isEmpty())
                && (courseIds == null || courseIds.isEmpty())) {
            return true;
        }
        if (students == null) return false;
        Long studentId = students.findByUserId(user.getId())
                .map(com.sis.entity.Student::getId)
                .orElse(null);
        if (studentId == null) return false;
        boolean programMatch = programIds != null && !programIds.isEmpty()
                && studentPrograms != null
                && studentPrograms.existsActiveByStudentIdAndProgramIds(studentId, programIds);
        if (programMatch) return true;
        if (courseIds != null && !courseIds.isEmpty() && courses != null) {
            for (Long courseId : courseIds) {
                if (courses.existsEnrolledStudent(courseId, studentId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isVisibleToAudience(AnnouncementAudience audience, Role role) {
        return role != null && (audience == AnnouncementAudience.ALL
                || ("ADMIN".equals(audience.name()) && role == Role.ADMIN)
                || ("TEACHER".equals(audience.name()) && role == Role.TEACHER)
                || ("STUDENT".equals(audience.name()) && role == Role.STUDENT));
    }
}
