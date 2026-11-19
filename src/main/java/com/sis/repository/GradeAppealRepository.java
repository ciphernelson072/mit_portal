package com.sis.repository;

import com.sis.entity.GradeAppeal;
import com.sis.entity.GradeAppealStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface GradeAppealRepository extends JpaRepository<GradeAppeal, Long> {
    boolean existsByGradeIdAndStatusIn(Long gradeId, Collection<GradeAppealStatus> statuses);

    @Query("SELECT appeal FROM GradeAppeal appeal JOIN FETCH appeal.grade grade "
            + "JOIN FETCH grade.student student JOIN FETCH grade.course "
            + "WHERE student.user.username = :username ORDER BY appeal.submittedAt DESC, appeal.id DESC")
    List<GradeAppeal> findByStudentUsername(@Param("username") String username);

    @Query("SELECT appeal FROM GradeAppeal appeal JOIN FETCH appeal.grade grade "
            + "JOIN FETCH grade.student JOIN FETCH grade.course "
            + "ORDER BY appeal.submittedAt, appeal.id")
    List<GradeAppeal> findAllWithGradeStudentAndCourse();

    @Query("SELECT appeal FROM GradeAppeal appeal JOIN FETCH appeal.grade grade "
            + "JOIN FETCH grade.student JOIN FETCH grade.course "
            + "WHERE grade.course.id IN :courseIds AND appeal.status = :status "
            + "ORDER BY appeal.submittedAt, appeal.id")
    List<GradeAppeal> findByCourseIdsAndStatus(@Param("courseIds") Collection<Long> courseIds,
                                               @Param("status") GradeAppealStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT appeal FROM GradeAppeal appeal JOIN FETCH appeal.grade grade "
            + "JOIN FETCH grade.student JOIN FETCH grade.course WHERE appeal.id = :id")
    Optional<GradeAppeal> findByIdForUpdate(@Param("id") Long id);
}
