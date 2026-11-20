package com.sis.service;

import com.sis.dto.AdmissionApplicationDocumentDto;
import com.sis.dto.AdmissionDocumentRequirementDto;
import com.sis.dto.AdmissionDocumentRequirementRequest;
import com.sis.entity.AdmissionApplication;
import com.sis.entity.AdmissionApplicationDocument;
import com.sis.entity.AdmissionDocumentRequirement;
import com.sis.repository.AdmissionApplicationDocumentRepository;
import com.sis.repository.AdmissionDocumentRequirementRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional
public class AdmissionDocumentService {
    private final AdmissionDocumentRequirementRepository requirements;
    private final AdmissionApplicationDocumentRepository documents;
    private final FileUploadService uploads;

    public AdmissionDocumentService(
            AdmissionDocumentRequirementRepository requirements,
            AdmissionApplicationDocumentRepository documents,
            FileUploadService uploads) {
        this.requirements = requirements;
        this.documents = documents;
        this.uploads = uploads;
    }

    @Transactional(readOnly = true)
    public List<AdmissionDocumentRequirementDto> listRequirements(boolean includeInactive) {
        List<AdmissionDocumentRequirement> result = includeInactive
                ? requirements.findAllByOrderBySortOrderAscIdAsc()
                : requirements.findByActiveTrueOrderBySortOrderAscIdAsc();
        return result.stream().map(AdmissionDocumentService::toRequirementDto).toList();
    }

    public AdmissionDocumentRequirementDto createRequirement(AdmissionDocumentRequirementRequest request) {
        String name = request.getName().trim();
        if (requirements.existsByNameIgnoreCase(name)) {
            throw new IllegalArgumentException("An admission document with that name already exists.");
        }
        AdmissionDocumentRequirement requirement = new AdmissionDocumentRequirement();
        requirement.setName(name);
        requirement.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        requirement.setRequired(request.isRequired());
        requirement.setSortOrder(request.getSortOrder());
        return toRequirementDto(requirements.save(requirement));
    }

    public AdmissionDocumentRequirementDto setRequirementActive(Long id, boolean active) {
        AdmissionDocumentRequirement requirement = requirements.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Admission document requirement not found."));
        requirement.setActive(active);
        return toRequirementDto(requirements.save(requirement));
    }

    @Transactional(readOnly = true)
    public boolean hasActiveRequiredDocuments() {
        return requirements.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .anyMatch(AdmissionDocumentRequirement::isRequired);
    }

    public List<AdmissionApplicationDocumentDto> storeForApplication(
            AdmissionApplication application, Map<String, MultipartFile> suppliedFiles) {
        List<AdmissionDocumentRequirement> activeRequirements =
                requirements.findByActiveTrueOrderBySortOrderAscIdAsc();
        Set<String> allowedFields = new HashSet<>();
        activeRequirements.forEach(requirement -> allowedFields.add(fieldName(requirement.getId())));
        suppliedFiles.forEach((field, file) -> {
            if (file != null && !file.isEmpty() && !allowedFields.contains(field)) {
                throw new IllegalArgumentException("An uploaded file does not match an active document requirement.");
            }
        });
        for (AdmissionDocumentRequirement requirement : activeRequirements) {
            MultipartFile file = suppliedFiles.get(fieldName(requirement.getId()));
            if (requirement.isRequired() && (file == null || file.isEmpty())) {
                throw new IllegalArgumentException("Upload the required document: " + requirement.getName());
            }
        }

        List<String> storedPaths = new ArrayList<>();
        List<AdmissionApplicationDocument> saved = new ArrayList<>();
        try {
            for (AdmissionDocumentRequirement requirement : activeRequirements) {
                MultipartFile file = suppliedFiles.get(fieldName(requirement.getId()));
                if (file == null || file.isEmpty()) {
                    continue;
                }
                String path = uploads.uploadAdmissionDocument(file, application.getId());
                storedPaths.add(path);
                AdmissionApplicationDocument document = new AdmissionApplicationDocument();
                document.setApplication(application);
                document.setRequirement(requirement);
                document.setFilePath(path);
                document.setContentType(file.getContentType() == null
                        ? "application/octet-stream" : file.getContentType());
                saved.add(documents.save(document));
            }
        } catch (IOException exception) {
            deleteStoredFiles(storedPaths);
            throw new IllegalArgumentException("Unable to store the uploaded admission documents.", exception);
        } catch (RuntimeException exception) {
            deleteStoredFiles(storedPaths);
            throw exception;
        }
        return saved.stream().map(AdmissionDocumentService::toApplicationDocumentDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AdmissionApplicationDocumentDto> listApplicationDocuments(Long applicationId) {
        return documents.findByApplicationIdOrderByUploadedAtAsc(applicationId).stream()
                .map(AdmissionDocumentService::toApplicationDocumentDto).toList();
    }

    @Transactional(readOnly = true)
    public AdmissionDocumentDownload download(Long applicationId, Long documentId) throws IOException {
        AdmissionApplicationDocument document = documents.findByIdAndApplicationId(documentId, applicationId)
                .orElseThrow(() -> new IOException("Admission document not found."));
        return new AdmissionDocumentDownload(
                uploads.getFileAsBytes(document.getFilePath()),
                document.getContentType(),
                document.getRequirement().getName());
    }

    private void deleteStoredFiles(List<String> storedPaths) {
        for (String path : storedPaths) {
            if (!uploads.deleteProfileImage(path)) {
                throw new IllegalStateException("Unable to remove a partially stored admission document.");
            }
        }
    }

    private static String fieldName(Long requirementId) {
        return "document_" + requirementId;
    }

    private static AdmissionDocumentRequirementDto toRequirementDto(AdmissionDocumentRequirement requirement) {
        return new AdmissionDocumentRequirementDto(
                requirement.getId(),
                requirement.getName(),
                requirement.getDescription(),
                requirement.isRequired(),
                requirement.isActive(),
                requirement.getSortOrder());
    }

    private static AdmissionApplicationDocumentDto toApplicationDocumentDto(
            AdmissionApplicationDocument document) {
        return new AdmissionApplicationDocumentDto(
                document.getId(),
                document.getRequirement().getId(),
                document.getRequirement().getName(),
                document.getRequirement().isRequired(),
                document.getUploadedAt());
    }

    public record AdmissionDocumentDownload(byte[] content, String contentType, String name) {
    }
}
