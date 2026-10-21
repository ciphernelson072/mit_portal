package com.sis.repository;

import com.sis.entity.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {
    Optional<AcademicYear> findFirstByActiveTrueOrderByStartDateDesc();
    List<AcademicYear> findAllByOrderByStartDateDesc();
}