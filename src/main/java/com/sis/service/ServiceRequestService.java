package com.sis.service;

import com.sis.dto.ServiceRequestDto;
import com.sis.dto.ServiceRequestEventDto;
import com.sis.dto.ServiceRequestAssigneeDto;
import com.sis.entity.AuditLog;
import com.sis.entity.ServiceRequestEvent;
import com.sis.entity.ServiceRequest;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.ServiceRequestRepository;
import com.sis.repository.ServiceRequestEventRepository;
import com.sis.repository.UserRepository;
import com.sis.entity.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@Service
@Transactional
public class ServiceRequestService {

    private final ServiceRequestRepository repository;
    private final AuditLogRepository auditLogs;
    private final NotificationService notifications;
    private final UserRepository users;
    private final ServiceRequestEventRepository events;
    private final NotificationRecipientService notificationRecipients;

    public ServiceRequestService(ServiceRequestRepository repository, AuditLogRepository auditLogs,
                                 NotificationService notifications, UserRepository users,
                                 ServiceRequestEventRepository events,
                                 NotificationRecipientService notificationRecipients) {
        this.repository = repository;
        this.auditLogs = auditLogs;
        this.notifications = notifications;
        this.users = users;
        this.events = events;
        this.notificationRecipients = notificationRecipients;
    }

    public ServiceRequestDto create(Student student, User requester, String type, String subject, String details) {
        ServiceRequest request = new ServiceRequest(student, requester, type, subject, details);
        ServiceRequest saved = repository.save(request);
        recordEvent(saved, requester, "CREATED", null, saved.getStatus(), null, null);
        ServiceRequestDto created = toDto(saved);
        notificationRecipients.findActiveUsersWithPermission("SERVICE_REQUEST_MANAGE", Role.ADMIN)
                .forEach(user -> notifications.create(user.getId(), "New service request",
                        "Request #" + created.getId() + " from " + student.getFullName() + ": " + subject));
        return created;
    }

    public ServiceRequestDto create(User requester, String type, String subject, String details) {
        ServiceRequest request = new ServiceRequest(null, requester, type, subject, details);
        ServiceRequest saved = repository.save(request);
        recordEvent(saved, requester, "CREATED", null, saved.getStatus(), null, null);
        ServiceRequestDto created = toDto(saved);
        notificationRecipients.findActiveUsersWithPermission("SERVICE_REQUEST_MANAGE", Role.ADMIN)
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
    public List<ServiceRequestAssigneeDto> listActiveAdminAssignees() {
        return users.findByRoleAndIsActiveTrueOrderByIdAsc(Role.ADMIN).stream()
                .map(user -> new ServiceRequestAssigneeDto(user.getId(), user.getUsername()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestEventDto> listHistory(Long requestId) {
        return events.findForRequestInOrder(requestId).stream()
                .map(event -> new ServiceRequestEventDto(
                        event.getEventType(), event.getActorUsername(), event.getFromStatus(),
                        event.getToStatus(), event.getFromAssigneeUsername(),
                        event.getToAssigneeUsername(), event.getCreatedAt()))
                .toList();
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
            if (previousStatus.equals(status)) {
                return toAdminDto(request);
            }
            User actor = findActor(updatedBy);
            LocalDateTime now = LocalDateTime.now();
            request.setStatus(status);
            request.setUpdatedAt(now);
            ServiceRequest saved = repository.save(request);
            recordEvent(saved, actor, "STATUS_CHANGED", previousStatus, status,
                    saved.getAssignedAdmin(), saved.getAssignedAdmin());
            auditLogs.save(new AuditLog(actor.getId(), "SERVICE_REQUEST_STATUS_UPDATED",
                "Request #" + saved.getId() + " status changed from " + previousStatus + " to " + status));
            Long requesterId = saved.getRequester() != null ? saved.getRequester().getId() : saved.getStudent().getUser().getId();
            notifications.create(requesterId, "Request status updated",
                    "Request #" + saved.getId() + " is now " + status + ".");
            return toAdminDto(saved);
        });
    }

    public Optional<ServiceRequestDto> updateAssignee(Long id, Long assigneeId, String updatedBy) {
        return repository.findById(id).map(request -> {
            User nextAssignee = assigneeId == null ? null : users.findById(assigneeId)
                    .filter(user -> user.getRole() == Role.ADMIN && Boolean.TRUE.equals(user.getIsActive()))
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.BAD_REQUEST, "The request owner must be an active administrator."));
            User previousAssignee = request.getAssignedAdmin();
            Long previousId = previousAssignee == null ? null : previousAssignee.getId();
            if (java.util.Objects.equals(previousId, assigneeId)) {
                return toAdminDto(request);
            }

            User actor = findActor(updatedBy);
            LocalDateTime now = LocalDateTime.now();
            request.setAssignedAdmin(nextAssignee);
            request.setAssignedAt(nextAssignee == null ? null : now);
            request.setUpdatedAt(now);
            ServiceRequest saved = repository.save(request);
            String eventType = previousAssignee == null
                    ? "ASSIGNED"
                    : nextAssignee == null ? "UNASSIGNED" : "REASSIGNED";
            recordEvent(saved, actor, eventType, saved.getStatus(), saved.getStatus(),
                    previousAssignee, nextAssignee);
            auditLogs.save(new AuditLog(actor.getId(), "SERVICE_REQUEST_ASSIGNEE_UPDATED",
                    "Request #" + saved.getId() + " assigned from "
                            + usernameOrUnassigned(previousAssignee) + " to "
                            + usernameOrUnassigned(nextAssignee)));
            if (previousAssignee != null) {
                notifications.create(previousAssignee.getId(), "Service request assignment updated",
                        nextAssignee == null
                                ? "Request #" + saved.getId() + " is no longer assigned to you."
                                : "Request #" + saved.getId() + " has been reassigned.");
            }
            if (nextAssignee != null) {
                notifications.create(nextAssignee.getId(), "Service request assigned",
                        "Request #" + saved.getId() + " has been assigned to you for review.");
            }
            return toAdminDto(saved);
        });
    }

    private User findActor(String username) {
        return users.findByUsernameNormalized(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + username));
    }

    private void recordEvent(ServiceRequest request, User actor, String eventType,
                             String fromStatus, String toStatus, User fromAssignee, User toAssignee) {
        ServiceRequestEvent event = new ServiceRequestEvent(
                request, actor, eventType, fromStatus, toStatus, fromAssignee, toAssignee);
        request.addEvent(event);
        events.save(event);
    }

    private String usernameOrUnassigned(User user) {
        return user == null ? "unassigned" : user.getUsername();
    }

    private ServiceRequestDto toDto(ServiceRequest request) {
        String username = request.getRequester() != null ? request.getRequester().getUsername() : null;
        String name = request.getStudent() != null ? request.getStudent().getFullName() : username;
        ServiceRequestDto dto = new ServiceRequestDto(request.getId(), request.getType(), request.getSubject(),
            request.getDetails(), request.getStatus(), request.getCreatedAt(), username, name,
            request.getRequesterRole());
        dto.setUpdatedAt(request.getUpdatedAt());
        return dto;
    }

    private ServiceRequestDto toAdminDto(ServiceRequest request) {
        String username = request.getRequester() != null ? request.getRequester().getUsername() : request.getStudent().getUser().getUsername();
        String name = request.getStudent() != null ? request.getStudent().getFullName() : username;
        ServiceRequestDto dto = new ServiceRequestDto(request.getId(), request.getType(), request.getSubject(),
                request.getDetails(), request.getStatus(), request.getCreatedAt(),
                username, name, request.getRequesterRole());
        dto.setUpdatedAt(request.getUpdatedAt());
        User assignee = request.getAssignedAdmin();
        if (assignee != null) {
            dto.setAssigneeId(assignee.getId());
            dto.setAssigneeUsername(assignee.getUsername());
        }
        dto.setAssignedAt(request.getAssignedAt());
        return dto;
    }
}
