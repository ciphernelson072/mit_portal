package com.sis.repository;

import com.sis.entity.Grade;
import com.sis.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.math.BigDecimal;
import com.sis.entity.GradeStatus;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

public interface GradeRepository extends JpaRepository<Grade, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Grade g WHERE g.id = :id")
    java.util.Optional<Grade> findByIdForUpdate(@Param("id") Long id);

    List<Grade> findByStudentId(Long studentId);

    @Query("SELECT c.id FROM Course c JOIN c.students s WHERE s.id = :studentId AND c.core = true")
    List<Long> findCoreCourseIdsByStudentId(@Param("studentId") Long studentId);

    List<Grade> findByStudentIdAndStatus(Long studentId, GradeStatus status);
    List<Grade> findByStudentIdAndCourseIdOrderByAttemptNumberAsc(Long studentId, Long courseId);
    List<Grade> findByStudentIdAndStatusOrderByApprovedAtDescIdDesc(Long studentId, GradeStatus status);
    List<Grade> findTop5ByStudentIdAndStatusOrderByApprovedAtDescIdDesc(Long studentId, GradeStatus status);
        boolean existsByStudentIdAndCourseIdAndStatusAndGpaScoreGreaterThanEqual(
            Long studentId, Long courseId, GradeStatus status, BigDecimal minimumGpa);
        boolean existsByCourseIdAndStatus(Long courseId, GradeStatus status);

    public List<Grade> findByStudent(Student student);

    @Query("SELECT g FROM Grade g JOIN FETCH g.student WHERE g.course.id = :courseId ORDER BY g.student.fullName")
    List<Grade> findByCourseIdWithStudent(@Param("courseId") Long courseId);

    @Query("SELECT g FROM Grade g JOIN FETCH g.student s JOIN FETCH s.user JOIN FETCH g.course "
            + "WHERE g.status IS NULL OR g.status = :status ORDER BY g.submittedAt DESC, g.id DESC")
    List<Grade> findPendingWithStudentUserAndCourse(@Param("status") GradeStatus status);

    @Query("SELECT g FROM Grade g JOIN FETCH g.student s JOIN FETCH s.user JOIN FETCH g.course "
            + "ORDER BY g.submittedAt DESC, g.id DESC")
    List<Grade> findAllWithStudentUserAndCourse();

    @Query("SELECT g FROM Grade g JOIN FETCH g.student s JOIN FETCH s.user JOIN FETCH g.course "
            + "WHERE g.id = :gradeId")
    java.util.Optional<Grade> findByIdWithStudentUserAndCourse(@Param("gradeId") Long gradeId);

    /**
     * JOIN FETCH course - the DTO mapping for every grade reads
     * grade.getCourse().getName(), which would otherwise be a separate query per row.
     */
    @Query("SELECT g FROM Grade g JOIN FETCH g.course WHERE g.student.id = :studentId")
    List<Grade> findByStudentIdWithCourse(Long studentId);

    @Query("SELECT COUNT(g) FROM Grade g WHERE g.course.id IN :courseIds "
            + "AND (g.status IS NULL OR g.status = :status)")
    long countByCourseIdsAndStatusOrNull(@Param("courseIds") List<Long> courseIds,
                                         @Param("status") GradeStatus status);
}
