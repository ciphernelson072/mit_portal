package com.sis.service;

import com.sis.dto.AdminEmailRecipientDto;
import com.sis.dto.BulkEmailRequest;
import com.sis.dto.BulkEmailResponse;
import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AdminEmailCampaignService {
    public static final int MAX_RECIPIENTS = 450;

    private static final Logger logger = LoggerFactory.getLogger(AdminEmailCampaignService.class);

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final EmailService emailService;

    public AdminEmailCampaignService(UserRepository userRepository, StudentRepository studentRepository,
                                     TeacherRepository teacherRepository, EmailService emailService) {
        this.userRepository = userRepository;
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.emailService = emailService;
    }

    @Transactional(readOnly = true)
    public List<AdminEmailRecipientDto> listRecipients() {
        List<User> activeUsers = userRepository.findByIsActiveTrueOrderByIdAsc();
        Map<Long, String> profileEmails = profileEmailsForUsers(activeUsers.stream()
                .map(User::getId)
                .collect(Collectors.toSet()));
        return activeUsers.stream()
                .filter(user -> Boolean.TRUE.equals(user.getIsActive()))
                .map(user -> {
                    String email = preferredEmail(user, profileEmails);
                    if (!isValidEmail(email)) {
                        return null;
                    }
                    return new AdminEmailRecipientDto(
                            user.getId(),
                            user.getUsername(),
                            user.getRole() == null ? "" : user.getRole().name(),
                            email.trim());
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    public BulkEmailResponse sendCampaign(BulkEmailRequest request) {
        if (request.getRecipientIds() == null || request.getRecipientIds().isEmpty()) {
            throw new IllegalArgumentException("Select at least one email recipient.");
        }

        Set<Long> uniqueIds = new LinkedHashSet<>(request.getRecipientIds());
        if (uniqueIds.contains(null)) {
            throw new IllegalArgumentException("Recipient IDs must not be empty.");
        }
        if (uniqueIds.size() > MAX_RECIPIENTS) {
            throw new IllegalArgumentException("A campaign can include at most " + MAX_RECIPIENTS + " recipients.");
        }

        String subject = request.getSubject().trim();
        String content = request.getContent().trim();
        if (subject.contains("\r") || subject.contains("\n")) {
            throw new IllegalArgumentException("Subject must be a single line.");
        }

        Map<Long, User> selectedUsers = new LinkedHashMap<>();
        userRepository.findAllById(uniqueIds).forEach(user -> selectedUsers.put(user.getId(), user));
        Map<Long, String> profileEmails = profileEmailsForUsers(uniqueIds);
        List<String> unavailableUsers = new ArrayList<>();
        for (Long id : uniqueIds) {
            User user = selectedUsers.get(id);
            if (user == null || !Boolean.TRUE.equals(user.getIsActive())
                    || !isValidEmail(preferredEmail(user, profileEmails))) {
                unavailableUsers.add(user == null ? "user #" + id : user.getUsername());
            }
        }
        if (!unavailableUsers.isEmpty()) {
            throw new IllegalArgumentException(
                    "Some selected accounts are no longer active or do not have a valid email. Refresh the recipient list before sending.");
        }

        int sentCount = 0;
        List<String> failedUsernames = new ArrayList<>();
        for (Long id : uniqueIds) {
            User user = selectedUsers.get(id);
            String recipientEmail = preferredEmail(user, profileEmails).trim();
            try {
                emailService.sendComposedEmail(recipientEmail, subject, content);
                sentCount++;
            } catch (IllegalStateException ex) {
                logger.warn("Email campaign delivery failed for user id {}", user.getId(), ex);
                failedUsernames.add(user.getUsername());
            }
        }
        return new BulkEmailResponse(uniqueIds.size(), sentCount, failedUsernames);
    }

    private Map<Long, String> profileEmailsForUsers(Set<Long> userIds) {
        Map<Long, String> profileEmails = new LinkedHashMap<>();
        if (userIds.isEmpty()) {
            return profileEmails;
        }
        for (Student student : studentRepository.findAllWithUserByUserIdIn(userIds)) {
            if (Boolean.TRUE.equals(student.getUser().getIsActive()) && isValidEmail(student.getEmail())) {
                profileEmails.putIfAbsent(student.getUser().getId(), student.getEmail().trim());
            }
        }
        for (Teacher teacher : teacherRepository.findAllWithUserByUserIdIn(userIds)) {
            if (Boolean.TRUE.equals(teacher.getUser().getIsActive()) && isValidEmail(teacher.getEmail())) {
                profileEmails.putIfAbsent(teacher.getUser().getId(), teacher.getEmail().trim());
            }
        }
        return profileEmails;
    }

    private String preferredEmail(User user, Map<Long, String> profileEmails) {
        return isValidEmail(user.getEmail()) ? user.getEmail() : profileEmails.get(user.getId());
    }

    private boolean isValidEmail(String email) {
        if (email == null || email.isBlank() || email.length() > 254) {
            return false;
        }
        try {
            InternetAddress address = new InternetAddress(email.trim(), true);
            address.validate();
            return address.getAddress().equals(email.trim());
        } catch (AddressException ex) {
            return false;
        }
    }
}
