package com.sis.repository;
import com.sis.entity.ScholarshipApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface ScholarshipApplicationRepository extends JpaRepository<ScholarshipApplication, Long> {
    List<ScholarshipApplication> findByStudentId(Long studentId);
    List<ScholarshipApplication> findByScholarshipIdOrderByAppliedAtAsc(Long scholarshipId);
    List<ScholarshipApplication> findAllByOrderByAppliedAtDesc();
    Optional<ScholarshipApplication> findByStudentIdAndScholarshipId(Long studentId, Long scholarshipId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select application from ScholarshipApplication application where application.id = :id")
    Optional<ScholarshipApplication> findByIdForUpdate(@Param("id") Long id);
}
