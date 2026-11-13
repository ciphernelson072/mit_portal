package com.sis.repository;

import com.sis.entity.StudentDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentDocumentRepository extends JpaRepository<StudentDocument, Long> {
    boolean existsByFilePath(String filePath);
    List<StudentDocument> findByStudentIdOrderByUploadedAtDesc(Long studentId);
    Optional<StudentDocument> findByIdAndStudentId(Long id, Long studentId);
}
