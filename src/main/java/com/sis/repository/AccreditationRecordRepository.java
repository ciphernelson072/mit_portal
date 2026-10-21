package com.sis.repository;

import com.sis.entity.AccreditationRecord;
import com.sis.entity.AccreditationStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface AccreditationRecordRepository extends JpaRepository<AccreditationRecord, Long> {
    boolean existsByEvidencePath(String evidencePath);
    @EntityGraph(attributePaths = {"program.department.faculty.campus"})
    List<AccreditationRecord> findByProgramIdOrderByValidUntilDesc(Long programId);

    @EntityGraph(attributePaths = {"program.department.faculty.campus"})
    List<AccreditationRecord> findByStatusOrderByValidUntilAsc(AccreditationStatus status);

    @EntityGraph(attributePaths = {"program.department.faculty.campus"})
    @Query("select record from AccreditationRecord record where record.id = :id")
    Optional<AccreditationRecord> findByIdWithProgramDirectory(@Param("id") Long id);
}