package com.sis.controller;

import com.sis.dto.CourseMaterialDto;
import com.sis.entity.Course;
import com.sis.entity.Role;
import com.sis.entity.User;
import com.sis.exception.ResourceNotFoundException;
import com.sis.repository.CourseMaterialRepository;
import com.sis.service.FileUploadService;
import com.sis.service.SchoolService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourseMaterialControllerTest {

    private final SchoolService schoolService = mock(SchoolService.class);
    private final FileUploadService fileUploadService = mock(FileUploadService.class);
    private final CourseMaterialRepository materials = mock(CourseMaterialRepository.class);
    private final CourseMaterialController controller =
            new CourseMaterialController(schoolService, fileUploadService, materials);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void administratorCanUploadMaterialToAnyCourse() throws Exception {
        authenticate("admin", "ADMIN");
        User administrator = user("admin", Role.ADMIN);
        Course course = course(7L);
        when(schoolService.getUserByUsername("admin")).thenReturn(Optional.of(administrator));
        when(schoolService.getCourseById(7L)).thenReturn(Optional.of(course));
        when(fileUploadService.uploadCourseMaterial(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(7L)))
                .thenReturn("materials/7/item.pdf");
        CourseMaterialDto created = new CourseMaterialDto();
        when(schoolService.createMaterial(course, "Guide", "Description",
                "materials/7/item.pdf", administrator)).thenReturn(created);

        var response = controller.createMaterial(7L, "Guide", "Description",
                new MockMultipartFile("file", "guide.pdf", "application/pdf", new byte[]{1}));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody()).isSameAs(created);
        verify(schoolService, never()).teacherOwnsCourse("admin", course);
    }

    @Test
    void assignedTeacherCanUploadAndUnassignedTeacherIsRejected() throws Exception {
        authenticate("teacher", "TEACHER");
        User teacher = user("teacher", Role.TEACHER);
        Course course = course(8L);
        when(schoolService.getUserByUsername("teacher")).thenReturn(Optional.of(teacher));
        when(schoolService.getCourseById(8L)).thenReturn(Optional.of(course));
        when(schoolService.teacherOwnsCourse("teacher", course)).thenReturn(true);
        when(fileUploadService.uploadCourseMaterial(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(8L)))
                .thenReturn("materials/8/item.pdf");
        when(schoolService.createMaterial(course, "Guide", "", "materials/8/item.pdf", teacher))
                .thenReturn(new CourseMaterialDto());

        var response = controller.createMaterial(8L, "Guide", "",
                new MockMultipartFile("file", "guide.pdf", "application/pdf", new byte[]{1}));

        assertThat(response.getStatusCode().value()).isEqualTo(201);
    }

    @Test
    void teacherCannotUploadToAnotherTeachersCourse() throws Exception {
        authenticate("teacher", "TEACHER");
        User teacher = user("teacher", Role.TEACHER);
        Course course = course(9L);
        when(schoolService.getUserByUsername("teacher")).thenReturn(Optional.of(teacher));
        when(schoolService.getCourseById(9L)).thenReturn(Optional.of(course));
        when(schoolService.teacherOwnsCourse("teacher", course)).thenReturn(false);

        var response = controller.createMaterial(9L, "Guide", "",
                new MockMultipartFile("file", "guide.pdf", "application/pdf", new byte[]{1}));

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(fileUploadService, never()).uploadCourseMaterial(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static void authenticate(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, "n/a",
                        AuthorityUtils.createAuthorityList(role)));
    }

    private static User user(String username, Role role) {
        User user = new User();
        user.setUsername(username);
        user.setRole(role);
        return user;
    }

    private static Course course(Long id) {
        Course course = new Course();
        course.setId(id);
        return course;
    }
}
