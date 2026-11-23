package com.sis.controller;

import com.sis.dto.AutomaticCourseAssignmentSummary;
import com.sis.dto.CreateCurriculumCourseRequest;
import com.sis.entity.Course;
import com.sis.entity.CurriculumCourse;
import com.sis.entity.Program;
import com.sis.service.AutomaticCourseAssignmentService;
import com.sis.service.CurriculumService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CurriculumControllerAutomationTest {
    private final CurriculumService curriculumService = mock(CurriculumService.class);
    private final AutomaticCourseAssignmentService automaticCourseAssignments =
            mock(AutomaticCourseAssignmentService.class);
    private final CurriculumController controller =
            new CurriculumController(curriculumService, automaticCourseAssignments);

    @Test
    void addingARequiredCourseReturnsAssignmentResultsForItsProgramSemester() {
        CreateCurriculumCourseRequest request = request(true);
        CurriculumCourse course = curriculumCourse(true);
        AutomaticCourseAssignmentSummary assignments =
                new AutomaticCourseAssignmentSummary(3, 2, 1, 0, List.of());
        when(curriculumService.addCourse(request)).thenReturn(course);
        when(automaticCourseAssignments.assignActiveStudentsForProgramSemester(5L, 2))
                .thenReturn(assignments);

        var response = controller.addCourse(request);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody())
                .containsEntry("programId", 5L)
                .containsEntry("semesterNumber", 2)
                .containsEntry("automaticAssignments", assignments);
        verify(automaticCourseAssignments).assignActiveStudentsForProgramSemester(5L, 2);
    }

    @Test
    void addingAnElectiveCourseDoesNotTriggerAutomaticAssignment() {
        CreateCurriculumCourseRequest request = request(false);
        when(curriculumService.addCourse(request)).thenReturn(curriculumCourse(false));

        var response = controller.addCourse(request);

        assertThat(response.getBody()).doesNotContainKey("automaticAssignments");
        verifyNoInteractions(automaticCourseAssignments);
    }

    private CreateCurriculumCourseRequest request(boolean required) {
        CreateCurriculumCourseRequest request = new CreateCurriculumCourseRequest();
        request.setProgramId(5L);
        request.setCourseId(8L);
        request.setSemesterNumber(2);
        request.setRequired(required);
        return request;
    }

    private CurriculumCourse curriculumCourse(boolean required) {
        Program program = new Program();
        program.setId(5L);
        Course course = new Course();
        course.setId(8L);
        course.setCourseCode("MIT101");
        course.setName("Portal Foundations");
        course.setCredits(3);
        CurriculumCourse curriculumCourse = new CurriculumCourse();
        curriculumCourse.setProgram(program);
        curriculumCourse.setCourse(course);
        curriculumCourse.setSemesterNumber(2);
        curriculumCourse.setRequired(required);
        return curriculumCourse;
    }
}
