package com.sis.service;

import com.sis.dto.AdmissionApplicationRequest;
import com.sis.entity.AdmissionApplication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Service
public class PublicAdmissionSubmissionService {
    private final AdmissionService admissions;
    private final AdmissionDocumentService documents;

    public PublicAdmissionSubmissionService(
            AdmissionService admissions, AdmissionDocumentService documents) {
        this.admissions = admissions;
        this.documents = documents;
    }

    @Transactional(rollbackFor = Exception.class)
    public AdmissionApplication submit(
            AdmissionApplicationRequest request, Map<String, MultipartFile> suppliedFiles) {
        AdmissionApplication application = admissions.submit(request);
        documents.storeForApplication(application, suppliedFiles);
        return application;
    }
}
