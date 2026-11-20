package com.sis.repository;

import com.sis.entity.AdmissionApplicationDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdmissionApplicationDocumentRepository
        extends JpaRepository<AdmissionApplicationDocument, Long> {
    List<AdmissionApplicationDocument> findByApplicationIdOrderByUploadedAtAsc(Long applicationId);
    Optional<AdmissionApplicationDocument> findByIdAndApplicationId(Long id, Long applicationId);
}
