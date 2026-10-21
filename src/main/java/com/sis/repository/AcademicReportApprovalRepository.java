package com.sis.repository;

import com.sis.entity.AcademicReportApproval;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AcademicReportApprovalRepository extends JpaRepository<AcademicReportApproval, Long> {

    Optional<AcademicReportApproval> findByStudentId(Long studentId);

    boolean existsByStudentId(Long studentId);
}
