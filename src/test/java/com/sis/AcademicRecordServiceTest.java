package com.sis;

import com.sis.dto.GradeDto;
import com.sis.dto.FeeDto;
import com.sis.entity.Attendance;
import com.sis.entity.Course;
import com.sis.entity.Grade;
import com.sis.entity.GradeStatus;
import com.sis.entity.Program;
import com.sis.entity.Student;
import com.sis.entity.StudentLifecycleStatus;
import com.sis.entity.StudentProgram;
import com.sis.repository.AttendanceRepository;
import com.sis.repository.GradeRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.StudentRepository;
import com.sis.service.AcademicRecordService;
import com.sis.service.FeeService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AcademicRecordServiceTest {

    @Test
    void toGradeDtoIncludesCourseMetadataFromBackendModel() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        FeeService feeService = Mockito.mock(FeeService.class);

        AcademicRecordService service = new AcademicRecordService(gradeRepository, attendanceRepository, feeService);

        Course course = new Course();
        course.setCourseCode("DBS401");
        course.setName("Database Systems");
        course.setCredits(3);
        course.setSemester("Semester 4");

        Grade grade = new Grade();
        grade.setCourse(course);
        grade.setGradeValue("A");
        grade.setGpaScore(BigDecimal.valueOf(4.0));
        grade.setRemarks("Excellent work");

        GradeDto dto = service.toGradeDto(grade);

        assertEquals("DBS401", dto.getCourseCode());
        assertEquals(3, dto.getCredits());
        assertEquals("Semester 4", dto.getSemester());
    }

    @Test
    void savingGradeReloadsCourseAndStudentForResponseMapping() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        FeeService feeService = Mockito.mock(FeeService.class);
        AcademicRecordService service = new AcademicRecordService(gradeRepository, attendanceRepository, feeService);

        Student student = new Student();
        student.setId(42L);
        Course course = new Course();
        course.setName("Database Systems");
        Grade saved = new Grade(student, course, "A", null);
        saved.setId(8L);
        when(gradeRepository.save(Mockito.any(Grade.class))).thenReturn(saved);
        when(gradeRepository.findByIdWithStudentUserAndCourse(8L)).thenReturn(java.util.Optional.of(saved));

        Grade result = service.saveGrade(student, course, "A", null, "teacher");

        assertEquals(saved, result);
        verify(gradeRepository).findByIdWithStudentUserAndCourse(8L);
    }

    @Test
    void toAttendanceDtoIncludesLateFlagAndClassTimes() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        FeeService feeService = Mockito.mock(FeeService.class);
        AcademicRecordService service = new AcademicRecordService(gradeRepository, attendanceRepository, feeService);

        Attendance attendance = new Attendance();
        attendance.setDate(LocalDate.of(2026, 9, 14));
        attendance.setPresent(true);
        attendance.setLate(true);
        attendance.setClassStartTime(LocalTime.of(9, 0));
        attendance.setClassEndTime(LocalTime.of(10, 30));

        var dto = service.toAttendanceDto(attendance);

        assertEquals(LocalTime.of(9, 0), dto.getClassStartTime());
        assertEquals(LocalTime.of(10, 30), dto.getClassEndTime());
        assertTrue(dto.isLate());
    }

    @Test
    void studentStatusIncludesLatestProgramAndLifecycleStatus() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        FeeService feeService = Mockito.mock(FeeService.class);
        StudentRepository studentRepository = Mockito.mock(StudentRepository.class);
        StudentProgramRepository studentPrograms = Mockito.mock(StudentProgramRepository.class);
        StudentProgram program = new StudentProgram();
        program.setStartDate(LocalDate.now());
        program.setStatus(StudentLifecycleStatus.SUSPENDED);
        Program studentProgram = new Program();
        studentProgram.setName("Bachelor of Computing");
        program.setProgram(studentProgram);
        StudentProgram previousProgram = new StudentProgram();
        previousProgram.setStartDate(LocalDate.now().minusYears(1));
        previousProgram.setStatus(StudentLifecycleStatus.ACTIVE);
        Program previous = new Program();
        previous.setName("Diploma of Computing");
        previousProgram.setProgram(previous);

        when(feeService.getFeesForStudent(42L)).thenReturn(Collections.emptyList());
        when(studentPrograms.findByStudentIdOrderByStartDateDesc(42L)).thenReturn(List.of(program, previousProgram));

        AcademicRecordService service = new AcademicRecordService(
                gradeRepository, attendanceRepository, feeService, studentRepository, studentPrograms);
        var status = service.getStudentStatus(42L);

        assertEquals("Bachelor of Computing", status.getStudentProgramName());
        assertEquals("SUSPENDED", status.getStudentLifecycleStatus());
    }

    @Test
    void studentStatusNextDueDateSkipsOverdueAndPaidFees() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        FeeService feeService = Mockito.mock(FeeService.class);
        LocalDate today = LocalDate.now();
        when(feeService.getFeesForStudent(42L)).thenReturn(List.of(
                new FeeDto(1L, "Overdue fee", BigDecimal.valueOf(100), false, today.minusDays(1), null),
                new FeeDto(2L, "Future fee", BigDecimal.valueOf(200), false, today.plusDays(5), null),
                new FeeDto(3L, "Paid fee", BigDecimal.valueOf(300), true, today.plusDays(2), today)
        ));

        AcademicRecordService service = new AcademicRecordService(gradeRepository, attendanceRepository, feeService);

        assertEquals(today.plusDays(5).toString(), service.getStudentStatus(42L).getNextDueDate());
    }

    @Test
    void studentStatusUsesNoneWhenAllUnpaidFeesAreOverdue() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        FeeService feeService = Mockito.mock(FeeService.class);
        LocalDate yesterday = LocalDate.now().minusDays(1);
        when(feeService.getFeesForStudent(42L)).thenReturn(List.of(
                new FeeDto(1L, "Overdue fee", BigDecimal.valueOf(100), false, yesterday, null)
        ));

        AcademicRecordService service = new AcademicRecordService(gradeRepository, attendanceRepository, feeService);

        assertEquals("None", service.getStudentStatus(42L).getNextDueDate());
    }

    @Test
    void studentStatusUsesRecentGradesAndAggregateAttendanceCounts() {
        GradeRepository gradeRepository = Mockito.mock(GradeRepository.class);
        AttendanceRepository attendanceRepository = Mockito.mock(AttendanceRepository.class);
        FeeService feeService = Mockito.mock(FeeService.class);
        Grade recentGrade = new Grade();
        recentGrade.setGradeValue("A");

        when(feeService.getFeesForStudent(42L)).thenReturn(Collections.emptyList());
        when(gradeRepository.findTop5ByStudentIdAndStatusOrderByApprovedAtDescIdDesc(42L, GradeStatus.APPROVED))
                .thenReturn(List.of(recentGrade));
        when(attendanceRepository.countByStudentId(42L)).thenReturn(25L);
        when(attendanceRepository.countByStudentIdAndPresentTrue(42L)).thenReturn(22L);

        var status = new AcademicRecordService(gradeRepository, attendanceRepository, feeService).getStudentStatus(42L);

        assertEquals(1, status.getGrades().size());
        assertEquals("A", status.getGrades().get(0).getGradeValue());
        assertEquals(25L, status.getAttendanceCount());
        assertEquals(22L, status.getPresentAttendanceCount());
        verify(gradeRepository).findTop5ByStudentIdAndStatusOrderByApprovedAtDescIdDesc(
                42L, GradeStatus.APPROVED);
    }
}
