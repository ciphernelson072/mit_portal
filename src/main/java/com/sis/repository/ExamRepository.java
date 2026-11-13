package com.sis.repository;
import com.sis.entity.Exam;
import com.sis.entity.RegistrationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
public interface ExamRepository extends JpaRepository<Exam, Long> {
    List<Exam> findByCourseIdIn(List<Long> courseIds);

    @Query("select distinct exam from Exam exam "
            + "where exam.courseId in (select course.id from Student student join student.courses course "
            + "where student.id = :studentId) "
            + "or exam.courseId in (select registration.section.course.id from CourseRegistration registration "
            + "where registration.student.id = :studentId and registration.status = :status)")
    List<Exam> findForStudentByEnrolledCourses(
            @Param("studentId") Long studentId, @Param("status") RegistrationStatus status);
}
