package com.sis.repository;

import com.sis.entity.AcademicTerm;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, Long> {
    @EntityGraph(attributePaths = "academicYear")
    List<AcademicTerm> findByAcademicYearIdOrderByStartDateAsc(Long academicYearId);
    List<AcademicTerm> findByRegistrationOpenTrueOrderByStartDateAsc();
    boolean existsByCodeIgnoreCase(String code);
    @Query("select term from AcademicTerm term join fetch term.academicYear where term.id = :id")
    Optional<AcademicTerm> findWithAcademicYearById(@Param("id") Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select term from AcademicTerm term where term.id = :id")
    Optional<AcademicTerm> findByIdForUpdate(@Param("id") Long id);
}