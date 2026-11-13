package com.sis.repository;

import com.sis.entity.AcademicResultSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.persistence.LockModeType;
import java.util.Optional;

public interface AcademicResultSnapshotRepository extends JpaRepository<AcademicResultSnapshot, Long> {

    Optional<AcademicResultSnapshot> findByStudentIdAndResultsVersion(Long studentId, String resultsVersion);

    Optional<AcademicResultSnapshot> findFirstByStudentIdOrderByApprovedAtDescIdDesc(Long studentId);

    Optional<AcademicResultSnapshot> findFirstByStudentIdAndPublishedAtIsNotNullOrderByPublishedAtDescIdDesc(
            Long studentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select snapshot from AcademicResultSnapshot snapshot where snapshot.id = :id")
    Optional<AcademicResultSnapshot> findByIdForUpdate(@Param("id") Long id);
}
