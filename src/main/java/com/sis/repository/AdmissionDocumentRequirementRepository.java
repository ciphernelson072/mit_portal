package com.sis.repository;

import com.sis.entity.AdmissionDocumentRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdmissionDocumentRequirementRepository
        extends JpaRepository<AdmissionDocumentRequirement, Long> {
    List<AdmissionDocumentRequirement> findAllByOrderBySortOrderAscIdAsc();
    List<AdmissionDocumentRequirement> findByActiveTrueOrderBySortOrderAscIdAsc();
    boolean existsByNameIgnoreCase(String name);
}
