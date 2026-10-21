package com.sis.repository;
import com.sis.entity.ScholarshipApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ScholarshipApplicationRepository extends JpaRepository<ScholarshipApplication, Long> { List<ScholarshipApplication> findByStudentId(Long studentId); }
