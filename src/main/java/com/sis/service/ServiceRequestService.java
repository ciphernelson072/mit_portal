package com.sis.service;

import com.sis.dto.ServiceRequestDto;
import com.sis.entity.AuditLog;
import com.sis.entity.ServiceRequest;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.ServiceRequestRepository;
import com.sis.repository.UserRepository;
import com.sis.entity.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class ServiceRequestService {

    private final ServiceRequestRepository repository;
    private final AuditLogRepository auditLogs;
    private final NotificationService notifications;
    private final UserRepository users;

    public ServiceRequestService(ServiceRequestRepository repository, AuditLogRepository auditLogs,
                                 NotificationService notifications, UserRepository users) {
        this.repository = repository;
        this.auditLogs = auditLogs;
        this.notifications = notifications;
        this.users = users;
    }

    public ServiceRequestDto create(Student student, User requester, String type, String subject, String details) {
        ServiceRequest request = new ServiceRequest(student, requester, type, subject, details);
        ServiceRequestDto created = toDto(repository.save(request));
        users.findByRoleAndIsActiveTrue(Role.ADMIN)
                .forEach(user -> notifications.create(user.getId(), "New service request",
                        "Request #" + created.getId() + " from " + student.getFullName() + ": " + subject));
        return created;
    }

    public ServiceRequestDto create(User requester, String type, String subject, String details) {
        ServiceRequest request = new ServiceRequest(null, requester, type, subject, details);
        ServiceRequestDto created = toDto(repository.save(request));
        users.findByRoleAndIsActiveTrue(Role.ADMIN)
                .forEach(user -> notifications.create(user.getId(), "New service request",
                    "Request #" + created.getId() + " from " + requester.getUsername() + ": " + subject));
        return created;
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestDto> listForStudent(Long studentId) {
        return repository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestDto> listForUser(Long userId) {
        return repository.findVisibleToUserOrderByCreatedAtDesc(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestDto> listAll() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toAdminDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Optional<ServiceRequestDto> findOne(Long id) {
        return repository.findById(id).map(this::toAdminDto);
    }

    @Transactional(readOnly = true)
    public Optional<ServiceRequestDto> findOneForStudent(Long id, Long studentId) {
        return repository.findById(id)
                .filter(request -> request.getStudent().getId().equals(studentId))
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Optional<ServiceRequestDto> findOneForUser(Long id, Long userId) {
        return repository.findById(id)
            .filter(request -> (request.getRequester() != null && request.getRequester().getId().equals(userId))
                || (request.getStudent() != null && request.getStudent().getUser().getId().equals(userId)))
                .map(this::toDto);
    }

        public Optional<ServiceRequestDto> updateStatus(Long id, String status, String updatedBy) {
        return repository.findById(id).map(request -> {
            String previousStatus = request.getStatus();
            request.setStatus(status);
            ServiceRequest saved = repository.save(request);
            User actor = users.findByUsernameNormalized(updatedBy)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + updatedBy));
            auditLogs.save(new AuditLog(actor.getId(), "SERVICE_REQUEST_STATUS_UPDATED",
                "Request #" + saved.getId() + " status changed from " + previousStatus + " to " + status));
            Long requesterId = saved.getRequester() != null ? saved.getRequester().getId() : saved.getStudent().getUser().getId();
            notifications.create(requesterId, "Request status updated",
                    "Request #" + saved.getId() + " is now " + status + ".");
            return toAdminDto(saved);
        });
    }

    private ServiceRequestDto toDto(ServiceRequest request) {
        String username = request.getRequester() != null ? request.getRequester().getUsername() : null;
        String name = request.getStudent() != null ? request.getStudent().getFullName() : username;
        return new ServiceRequestDto(request.getId(), request.getType(), request.getSubject(),
            request.getDetails(), request.getStatus(), request.getCreatedAt(), username, name,
            request.getRequesterRole());
    }

    private ServiceRequestDto toAdminDto(ServiceRequest request) {
        String username = request.getRequester() != null ? request.getRequester().getUsername() : request.getStudent().getUser().getUsername();
        String name = request.getStudent() != null ? request.getStudent().getFullName() : username;
        return new ServiceRequestDto(request.getId(), request.getType(), request.getSubject(),
                request.getDetails(), request.getStatus(), request.getCreatedAt(),
                username, name, request.getRequesterRole());
    }
}
