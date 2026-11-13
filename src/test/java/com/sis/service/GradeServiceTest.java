package com.sis.service;

import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GradeServiceTest {

    @Test
    void refusesToUpdateApprovedGrade() {
        GradeRepository grades = mock(GradeRepository.class);
        GradeService service = service(grades);
        Grade approved = new Grade();
        approved.setStatus(GradeStatus.APPROVED);
        when(grades.findById(7L)).thenReturn(Optional.of(approved));

        assertThrows(IllegalStateException.class, () -> service.updateGrade(7L, new Grade()));

        verify(grades, never()).save(approved);
    }

    @Test
    void refusesToDeleteApprovedGrade() {
        GradeRepository grades = mock(GradeRepository.class);
        GradeService service = service(grades);
        Grade approved = new Grade();
        approved.setStatus(GradeStatus.APPROVED);
        when(grades.findById(7L)).thenReturn(Optional.of(approved));

        assertThrows(IllegalStateException.class, () -> service.deleteGrade(7L));

        verify(grades, never()).deleteById(7L);
    }

    private GradeService service(GradeRepository grades) {
        GradeService service = new GradeService();
        ReflectionTestUtils.setField(service, "gradeRepository", grades);
        ReflectionTestUtils.setField(service, "studentRepository", mock(StudentRepository.class));
        return service;
    }
}
