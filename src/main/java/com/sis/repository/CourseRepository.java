package com.sis.repository;

import com.sis.entity.Course;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import javax.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface CourseRepository extends JpaRepository<Course, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select course from Course course where course.id in :ids order by course.id")
    List<Course> findAllByIdInOrderByIdForUpdate(@Param("ids") List<Long> ids);


    List<Course> findByTeacherId(Long teacherId);
    Optional<Course> findByCourseCode(String courseCode);

    boolean existsByIdAndTeacherUserUsername(Long courseId, String username);

    long countByCategoryId(Long categoryId);

    /** JOIN FETCH the teacher - every DTO mapping here reads course.getTeacher(). */
    @Query(value = "SELECT c FROM Course c JOIN FETCH c.teacher",
           countQuery = "SELECT COUNT(c) FROM Course c")
    Page<Course> findAllWithTeacher(Pageable pageable);

    @Query("SELECT c FROM Course c JOIN FETCH c.teacher")
    List<Course> findAllWithTeacher();

    @Query("SELECT c FROM Course c JOIN FETCH c.teacher WHERE c.teacher.id = :teacherId")
    List<Course> findByTeacherIdWithTeacher(Long teacherId);

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Course c "
            + "JOIN c.students student WHERE c.teacher.id = :teacherId AND student.id = :studentId")
    boolean existsAssignedStudent(@Param("teacherId") Long teacherId, @Param("studentId") Long studentId);

    @Query("SELECT CASE WHEN COUNT(course.id) > 0 THEN true ELSE false END FROM Course course "
            + "WHERE course.id = :courseId AND ("
            + "EXISTS (SELECT student.id FROM Course legacyCourse JOIN legacyCourse.students student "
            + "WHERE legacyCourse.id = course.id AND student.id = :studentId) OR "
            + "EXISTS (SELECT registration.id FROM CourseRegistration registration "
            + "WHERE registration.student.id = :studentId "
            + "AND registration.section.course.id = course.id "
            + "AND registration.status = com.sis.entity.RegistrationStatus.REGISTERED))")
    boolean existsEnrolledStudent(@Param("courseId") Long courseId, @Param("studentId") Long studentId);

    @Query("SELECT COUNT(DISTINCT student.id) FROM Course c JOIN c.students student WHERE c.id IN :courseIds")
    long countDistinctStudentsByCourseIds(@Param("courseIds") List<Long> courseIds);

    @Query("SELECT COUNT(student.id) FROM Course c JOIN c.students student WHERE c.id IN :courseIds "
            + "AND NOT EXISTS (SELECT a.id FROM Attendance a WHERE a.course.id = c.id "
            + "AND a.student.id = student.id AND a.date = :date)")
    long countStudentsWithoutAttendanceByCourseIdsAndDate(@Param("courseIds") List<Long> courseIds,
                                                          @Param("date") LocalDate date);
}
