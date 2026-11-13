package com.sis.service;

import com.sis.dto.CreateCourseCategoryRequest;
import com.sis.entity.CourseCategory;
import com.sis.exception.DuplicateResourceException;
import com.sis.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchoolServiceCourseCategoryTest {

    @Test
    void createCourseCategoryNormalizesCodeAndTrimsName() {
        CourseCategoryRepository categories = mock(CourseCategoryRepository.class);
        when(categories.findByCodeIgnoreCase("HUMANITIES")).thenReturn(Optional.empty());
        when(categories.findByNameIgnoreCase("Social Sciences and History")).thenReturn(Optional.empty());
        when(categories.save(any(CourseCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service(categories).createCourseCategory(request(" humanities ", " Social Sciences and History "));

        assertEquals("HUMANITIES", result.getCode());
        assertEquals("Social Sciences and History", result.getName());
    }

    @Test
    void createCourseCategoryRejectsDuplicateCodeBeforeDatabaseInsert() {
        CourseCategoryRepository categories = mock(CourseCategoryRepository.class);
        when(categories.findByCodeIgnoreCase("HUMANITIES"))
                .thenReturn(Optional.of(new CourseCategory("HUMANITIES", "Humanities")));

        DuplicateResourceException error = assertThrows(
                DuplicateResourceException.class,
                () -> service(categories).createCourseCategory(request("humanities", "Social Sciences and History"))
        );

        assertEquals("A course category with this code already exists.", error.getMessage());
        verify(categories, never()).save(any(CourseCategory.class));
    }

    @Test
    void createCourseCategoryRejectsDuplicateNameBeforeDatabaseInsert() {
        CourseCategoryRepository categories = mock(CourseCategoryRepository.class);
        when(categories.findByCodeIgnoreCase("HUMANITIES")).thenReturn(Optional.empty());
        when(categories.findByNameIgnoreCase("Social Sciences and History"))
                .thenReturn(Optional.of(new CourseCategory("HISTORY", "Social Sciences and History")));

        DuplicateResourceException error = assertThrows(
                DuplicateResourceException.class,
                () -> service(categories).createCourseCategory(request("humanities", "Social Sciences and History"))
        );

        assertEquals("A course category with this name already exists.", error.getMessage());
        verify(categories, never()).save(any(CourseCategory.class));
    }

    private static CreateCourseCategoryRequest request(String code, String name) {
        CreateCourseCategoryRequest request = new CreateCourseCategoryRequest();
        request.setCode(code);
        request.setName(name);
        return request;
    }

    private static SchoolService service(CourseCategoryRepository categories) {
        return new SchoolService(
                mock(StudentRepository.class),
                mock(TeacherRepository.class),
                mock(CourseRepository.class),
                mock(GradeRepository.class),
                mock(CourseUnitRepository.class),
                mock(AttendanceRepository.class),
                mock(UserRepository.class),
                categories,
                mock(FeeRepository.class),
                mock(AssignmentRepository.class),
                mock(PasswordEncoder.class),
                mock(AcademicRecordService.class),
                mock(FeeService.class),
                mock(MessagingService.class),
                mock(CourseMaterialService.class)
        );
    }
}
