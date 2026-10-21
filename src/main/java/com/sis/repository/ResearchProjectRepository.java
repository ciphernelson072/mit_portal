package com.sis.repository;

import com.sis.entity.ResearchProject;
import com.sis.entity.ResearchProjectStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ResearchProjectRepository extends JpaRepository<ResearchProject, Long> {
    List<ResearchProject> findByStudentIdOrderByCreatedAtDesc(Long studentId);
    List<ResearchProject> findByStatusOrderByCreatedAtAsc(ResearchProjectStatus status);
}