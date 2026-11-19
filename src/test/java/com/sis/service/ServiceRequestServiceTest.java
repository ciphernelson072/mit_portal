package com.sis.service;

import com.sis.entity.Role;
import com.sis.dto.ServiceRequestDto;
import com.sis.entity.ServiceRequest;
import com.sis.entity.ServiceRequestEvent;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.ServiceRequestRepository;
import com.sis.repository.ServiceRequestEventRepository;
import com.sis.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.argThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ServiceRequestServiceTest {

    @Test
    void studentRequestNotifiesOnlyActiveAdministratorsUsingScopedLookup() {
        ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        UserRepository users = mock(UserRepository.class);
        ServiceRequestEventRepository events = mock(ServiceRequestEventRepository.class);
        NotificationRecipientService recipients = mock(NotificationRecipientService.class);
        ServiceRequestService service = new ServiceRequestService(
                requests, mock(AuditLogRepository.class), notifications, users, events, recipients);
        User requester = new User("student", "n/a", Role.STUDENT);
        Student student = new Student();
        student.setFullName("Student Name");
        User administrator = new User("admin", "n/a", Role.ADMIN);
        administrator.setId(41L);
        when(requests.save(any(ServiceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipients.findActiveUsersWithPermission("SERVICE_REQUEST_MANAGE", Role.ADMIN))
                .thenReturn(List.of(administrator));

        service.create(student, requester, "DOCUMENT", "Transcript", "Please provide a transcript");

        verify(recipients).findActiveUsersWithPermission("SERVICE_REQUEST_MANAGE", Role.ADMIN);
        verify(users, never()).findAll();
        verify(notifications).create(eq(41L), eq("New service request"), contains("Student Name: Transcript"));
        verify(events).save(argThat(event -> "CREATED".equals(event.getEventType())
                && "OPEN".equals(event.getToStatus())
                && "student".equals(event.getActorUsername())));
    }

    @Test
    void teacherRequestNotifiesActiveAdministratorsWithoutLoadingAllUsers() {
        ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        UserRepository users = mock(UserRepository.class);
        ServiceRequestEventRepository events = mock(ServiceRequestEventRepository.class);
        NotificationRecipientService recipients = mock(NotificationRecipientService.class);
        ServiceRequestService service = new ServiceRequestService(
                requests, mock(AuditLogRepository.class), notifications, users, events, recipients);
        User requester = new User("teacher", "n/a", Role.TEACHER);
        User administrator = new User("admin", "n/a", Role.ADMIN);
        administrator.setId(42L);
        when(requests.save(any(ServiceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipients.findActiveUsersWithPermission("SERVICE_REQUEST_MANAGE", Role.ADMIN))
                .thenReturn(List.of(administrator));

        service.create(requester, "IT_SUPPORT", "Projector", "Room projector is not working");

        verify(recipients).findActiveUsersWithPermission("SERVICE_REQUEST_MANAGE", Role.ADMIN);
        verify(users, never()).findAll();
        verify(notifications).create(eq(42L), eq("New service request"), contains("from teacher: Projector"));
    }

    @Test
    void userRequestListUsesOneAlreadyOrderedOwnershipScopedQuery() {
        ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
        ServiceRequestEventRepository events = mock(ServiceRequestEventRepository.class);
        ServiceRequestService service = new ServiceRequestService(
                requests, mock(AuditLogRepository.class), mock(NotificationService.class),
                mock(UserRepository.class), events, mock(NotificationRecipientService.class));
        when(requests.findVisibleToUserOrderByCreatedAtDesc(17L)).thenReturn(List.of());

        List<ServiceRequestDto> result = service.listForUser(17L);

        org.assertj.core.api.Assertions.assertThat(result).isEmpty();
        verify(requests).findVisibleToUserOrderByCreatedAtDesc(17L);
        verify(requests, never()).findByRequesterIdOrderByCreatedAtDesc(17L);
        verify(requests, never()).findByStudentUserIdOrderByCreatedAtDesc(17L);
    }

    @Test
    void statusChangesAppendActorAndTimestampHistoryOnlyWhenStatusChanges() {
        ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        UserRepository users = mock(UserRepository.class);
        ServiceRequestEventRepository events = mock(ServiceRequestEventRepository.class);
        ServiceRequestService service = new ServiceRequestService(
                requests, auditLogs, notifications, users, events, mock(NotificationRecipientService.class));
        User requester = new User("student", "n/a", Role.STUDENT);
        requester.setId(17L);
        Student student = new Student();
        student.setFullName("Student Name");
        ServiceRequest request = new ServiceRequest(student, requester, "SUPPORT", "Help", "Need help");
        request.setStatus("OPEN");
        when(requests.findById(81L)).thenReturn(java.util.Optional.of(request));
        when(requests.save(any(ServiceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User admin = new User("admin", "n/a", Role.ADMIN);
        admin.setId(3L);
        when(users.findByUsernameNormalized("admin")).thenReturn(java.util.Optional.of(admin));

        service.updateStatus(81L, "IN_PROGRESS", "admin").orElseThrow();

        verify(events).save(argThat(event -> "STATUS_CHANGED".equals(event.getEventType())
                && "OPEN".equals(event.getFromStatus())
                && "IN_PROGRESS".equals(event.getToStatus())
                && "admin".equals(event.getActorUsername())
                && event.getCreatedAt() != null));
        verify(notifications).create(17L, "Request status updated", "Request #null is now IN_PROGRESS.");
        org.junit.jupiter.api.Assertions.assertNotNull(request.getUpdatedAt());
    }

    @Test
    void unchangedStatusDoesNotCreateDuplicateHistoryOrNotification() {
        ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        ServiceRequestEventRepository events = mock(ServiceRequestEventRepository.class);
        Student student = new Student();
        student.setFullName("Student Name");
        ServiceRequest request = new ServiceRequest(student, new User("student", "n/a", Role.STUDENT),
                "SUPPORT", "Help", "Need help");
        when(requests.findById(83L)).thenReturn(java.util.Optional.of(request));
        ServiceRequestService service = new ServiceRequestService(
                requests, mock(AuditLogRepository.class), notifications, mock(UserRepository.class),
                events, mock(NotificationRecipientService.class));

        service.updateStatus(83L, "OPEN", "admin").orElseThrow();

        verify(requests, never()).save(any(ServiceRequest.class));
        verify(events, never()).save(any(ServiceRequestEvent.class));
        verify(notifications, never()).create(any(), any(), any());
    }

    @Test
    void assignmentIsRestrictedToActiveAdministratorsAndRecordedWithAuditAndHistory() {
        ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
        AuditLogRepository auditLogs = mock(AuditLogRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        UserRepository users = mock(UserRepository.class);
        ServiceRequestEventRepository events = mock(ServiceRequestEventRepository.class);
        ServiceRequestService service = new ServiceRequestService(
                requests, auditLogs, notifications, users, events, mock(NotificationRecipientService.class));
        User requester = new User("student", "n/a", Role.STUDENT);
        requester.setId(17L);
        Student student = new Student();
        student.setFullName("Student Name");
        ServiceRequest request = new ServiceRequest(student, requester, "SUPPORT", "Help", "Need help");
        when(requests.findById(82L)).thenReturn(java.util.Optional.of(request));
        when(requests.save(any(ServiceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        User admin = new User("admin", "n/a", Role.ADMIN);
        admin.setId(3L);
        when(users.findByUsernameNormalized("admin")).thenReturn(java.util.Optional.of(admin));
        User assignee = new User("admin2", "n/a", Role.ADMIN);
        assignee.setId(4L);
        when(users.findById(4L)).thenReturn(java.util.Optional.of(assignee));

        ServiceRequestDto updated = service.updateAssignee(82L, 4L, "admin").orElseThrow();

        org.junit.jupiter.api.Assertions.assertEquals(4L, updated.getAssigneeId());
        org.junit.jupiter.api.Assertions.assertEquals("admin2", updated.getAssigneeUsername());
        org.junit.jupiter.api.Assertions.assertNotNull(updated.getAssignedAt());
        verify(events).save(argThat(event -> "ASSIGNED".equals(event.getEventType())
                && "admin".equals(event.getActorUsername())
                && "admin2".equals(event.getToAssigneeUsername())
                && event.getCreatedAt() != null));
        verify(auditLogs).save(argThat(audit -> "SERVICE_REQUEST_ASSIGNEE_UPDATED".equals(audit.getAction())));
        verify(notifications).create(eq(4L), eq("Service request assigned"),
                contains("has been assigned to you for review."));

        User teacher = new User("teacher", "n/a", Role.TEACHER);
        when(users.findById(5L)).thenReturn(java.util.Optional.of(teacher));
        User inactiveAdmin = new User("inactive-admin", "n/a", Role.ADMIN);
        inactiveAdmin.setIsActive(false);
        when(users.findById(6L)).thenReturn(java.util.Optional.of(inactiveAdmin));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.updateAssignee(82L, 5L, "admin"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> service.updateAssignee(82L, 6L, "admin"));
        verify(events, times(1)).save(any(ServiceRequestEvent.class));
    }

    @Test
    void reassignmentNotifiesBothPreviousAndNewAssignees() {
        ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        UserRepository users = mock(UserRepository.class);
        ServiceRequestEventRepository events = mock(ServiceRequestEventRepository.class);
        User previousAssignee = new User("admin1", "n/a", Role.ADMIN);
        previousAssignee.setId(4L);
        User nextAssignee = new User("admin2", "n/a", Role.ADMIN);
        nextAssignee.setId(5L);
        User actor = new User("admin3", "n/a", Role.ADMIN);
        actor.setId(6L);
        ServiceRequest request = new ServiceRequest(new Student(), new User("student", "n/a", Role.STUDENT),
                "SUPPORT", "Help", "Need help");
        request.setAssignedAdmin(previousAssignee);
        when(requests.findById(91L)).thenReturn(java.util.Optional.of(request));
        when(requests.save(any(ServiceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findById(5L)).thenReturn(java.util.Optional.of(nextAssignee));
        when(users.findByUsernameNormalized("admin3")).thenReturn(java.util.Optional.of(actor));

        ServiceRequestService service = new ServiceRequestService(
                requests, mock(AuditLogRepository.class), notifications, users, events,
                mock(NotificationRecipientService.class));

        service.updateAssignee(91L, 5L, "admin3").orElseThrow();

        verify(notifications).create(eq(4L), eq("Service request assignment updated"),
                contains("has been reassigned."));
        verify(notifications).create(eq(5L), eq("Service request assigned"),
                contains("has been assigned to you for review."));
    }
}
