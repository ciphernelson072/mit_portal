package com.sis.repository;

import com.sis.entity.ThesisSubmission;
import com.sis.entity.ThesisSubmissionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ThesisSubmissionRepository extends JpaRepository<ThesisSubmission, Long> {
    boolean existsByFilePath(String filePath);
    List<ThesisSubmission> findByResearchProjectIdOrderBySubmittedAtDesc(Long researchProjectId);
    List<ThesisSubmission> findByResearchProjectIdInOrderBySubmittedAtDesc(List<Long> researchProjectIds);
    List<ThesisSubmission> findByStatusOrderBySubmittedAtAsc(ThesisSubmissionStatus status);
}