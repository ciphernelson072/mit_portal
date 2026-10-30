package com.sis.service;

import com.sis.entity.Role;
import com.sis.dto.ServiceRequestDto;
import com.sis.entity.ServiceRequest;
import com.sis.entity.Student;
import com.sis.entity.User;
import com.sis.repository.AuditLogRepository;
import com.sis.repository.ServiceRequestRepository;
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

class ServiceRequestServiceTest {

    @Test
    void studentRequestNotifiesOnlyActiveAdministratorsUsingScopedLookup() {
        ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        UserRepository users = mock(UserRepository.class);
        ServiceRequestService service = new ServiceRequestService(
                requests, mock(AuditLogRepository.class), notifications, users);
        User requester = new User("student", "n/a", Role.STUDENT);
        Student student = new Student();
        student.setFullName("Student Name");
        User administrator = new User("admin", "n/a", Role.ADMIN);
        administrator.setId(41L);
        when(requests.save(any(ServiceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(List.of(administrator));

        service.create(student, requester, "DOCUMENT", "Transcript", "Please provide a transcript");

        verify(users).findByRoleAndIsActiveTrue(Role.ADMIN);
        verify(users, never()).findAll();
        verify(notifications).create(eq(41L), eq("New service request"), contains("Student Name: Transcript"));
    }

    @Test
    void teacherRequestNotifiesActiveAdministratorsWithoutLoadingAllUsers() {
        ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
        NotificationService notifications = mock(NotificationService.class);
        UserRepository users = mock(UserRepository.class);
        ServiceRequestService service = new ServiceRequestService(
                requests, mock(AuditLogRepository.class), notifications, users);
        User requester = new User("teacher", "n/a", Role.TEACHER);
        User administrator = new User("admin", "n/a", Role.ADMIN);
        administrator.setId(42L);
        when(requests.save(any(ServiceRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.findByRoleAndIsActiveTrue(Role.ADMIN)).thenReturn(List.of(administrator));

        service.create(requester, "IT_SUPPORT", "Projector", "Room projector is not working");

        verify(users).findByRoleAndIsActiveTrue(Role.ADMIN);
        verify(users, never()).findAll();
        verify(notifications).create(eq(42L), eq("New service request"), contains("from teacher: Projector"));
    }

    @Test
    void userRequestListUsesOneAlreadyOrderedOwnershipScopedQuery() {
        ServiceRequestRepository requests = mock(ServiceRequestRepository.class);
        ServiceRequestService service = new ServiceRequestService(
                requests, mock(AuditLogRepository.class), mock(NotificationService.class), mock(UserRepository.class));
        when(requests.findVisibleToUserOrderByCreatedAtDesc(17L)).thenReturn(List.of());

        List<ServiceRequestDto> result = service.listForUser(17L);

        org.assertj.core.api.Assertions.assertThat(result).isEmpty();
        verify(requests).findVisibleToUserOrderByCreatedAtDesc(17L);
        verify(requests, never()).findByRequesterIdOrderByCreatedAtDesc(17L);
        verify(requests, never()).findByStudentUserIdOrderByCreatedAtDesc(17L);
    }
}
