package com.sis.repository;

import com.sis.entity.ProgramOutcome;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface ProgramOutcomeRepository extends JpaRepository<ProgramOutcome, Long> {
    @EntityGraph(attributePaths = {"program.department.faculty.campus"})
    List<ProgramOutcome> findByProgramIdAndActiveTrueOrderByCodeAsc(Long programId);

    @EntityGraph(attributePaths = {"program.department.faculty.campus"})
    @Query("select outcome from ProgramOutcome outcome where outcome.id = :id")
    Optional<ProgramOutcome> findByIdWithProgramDirectory(@Param("id") Long id);
}