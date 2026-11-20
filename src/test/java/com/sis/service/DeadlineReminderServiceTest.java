package com.sis.service;

import com.sis.entity.AcademicTerm;
import com.sis.entity.Course;
import com.sis.entity.CourseSection;
import com.sis.entity.GradeStatus;
import com.sis.entity.NotificationCategory;
import com.sis.entity.RegistrationStatus;
import com.sis.entity.Role;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.repository.AcademicTermRepository;
import com.sis.repository.CourseRegistrationRepository;
import com.sis.repository.CourseSectionRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeadlineReminderServiceTest {
    private final FeeService fees = mock(FeeService.class);
    private final AcademicTermRepository academicTerms = mock(AcademicTermRepository.class);
    private final CourseSectionRepository sections = mock(CourseSectionRepository.class);
    private final CourseRegistrationRepository registrations = mock(CourseRegistrationRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final DeadlineReminderService service = new DeadlineReminderService(
            fees, academicTerms, sections, registrations, notifications, "Africa/Nairobi");

    @Test
    void createsIdempotentFeeReminderSevenDaysBeforeDueDate() {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));
        LocalDate dueDate = today.plusDays(7);
        when(fees.feeReminderCandidates(today.plusDays(7))).thenReturn(List.of(
                new FeeService.FeeReminderCandidate(
                        55L, 19L, 8L, "Tuition", dueDate, new BigDecimal("250000"))));
        when(academicTerms
                .findByGradeSubmissionDeadlineIsNotNullAndGradeSubmissionDeadlineLessThanEqualOrderByGradeSubmissionDeadlineAsc(
                        today.plusDays(7))).thenReturn(List.of());

        service.sendDailyReminders();

        verify(notifications).createAutomated(
                "fee:55:" + dueDate + ":before-7",
                8L,
                "Fee deadline reminder",
                "The fee \"Tuition\" has an outstanding balance of 250000 and is due on " + dueDate + ".",
                NotificationCategory.PAYMENT);
    }

    @Test
    void remindsOnlyTeachersWithOutstandingApprovedGradesForTheConfiguredTermDeadline() {
        LocalDate today = LocalDate.now(ZoneId.of("Africa/Nairobi"));
        LocalDate deadline = today.plusDays(3);
        AcademicTerm term = new AcademicTerm();
        term.setId(11L);
        term.setName("Semester One");
        term.setGradeSubmissionDeadline(deadline);
        when(fees.feeReminderCandidates(today.plusDays(7))).thenReturn(List.of());
        when(academicTerms
                .findByGradeSubmissionDeadlineIsNotNullAndGradeSubmissionDeadlineLessThanEqualOrderByGradeSubmissionDeadlineAsc(
                        today.plusDays(7))).thenReturn(List.of(term));
        when(registrations.findCourseIdsWithMissingApprovedGradesByTerm(
                11L, RegistrationStatus.REGISTERED, GradeStatus.APPROVED)).thenReturn(List.of(50L));
        User user = new User("teacher", "encoded", Role.TEACHER);
        user.setId(8L);
        user.setIsActive(true);
        Course course = new Course();
        course.setId(50L);
        course.setCourseCode("CSC101");
        course.setName("Programming");
        course.setTeacher(new Teacher(user, "Instructor"));
        CourseSection section = new CourseSection();
        section.setCourse(course);
        when(sections.findByAcademicTermIdAndActiveTrueOrderByCourseCourseCodeAsc(11L))
                .thenReturn(List.of(section));

        service.sendDailyReminders();

        verify(notifications).createAutomated(
                "grade:11:50:8:" + deadline + ":before-3",
                8L,
                "Grade submission deadline reminder",
                "Approved grades are still missing for CSC101 in Semester One. The submission deadline is "
                        + deadline + ".",
                NotificationCategory.GRADE);
    }
}
