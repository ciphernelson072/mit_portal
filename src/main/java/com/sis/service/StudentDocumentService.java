package com.sis.service;

import com.sis.dto.StudentDocumentDto;
import com.sis.entity.Student;
import com.sis.entity.StudentDocument;
import com.sis.repository.StudentDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class StudentDocumentService {
    private final StudentDocumentRepository repository;
    private final FileUploadService fileUploadService;

    public StudentDocumentService(StudentDocumentRepository repository, FileUploadService fileUploadService) {
        this.repository = repository;
        this.fileUploadService = fileUploadService;
    }

    public List<StudentDocumentDto> list(Long studentId) {
        return repository.findByStudentIdOrderByUploadedAtDesc(studentId).stream().map(this::toDto).collect(Collectors.toList());
    }

    public StudentDocumentDto upload(Student student, String name, MultipartFile file) throws IOException {
        String path = fileUploadService.uploadStudentDocument(file, student.getId());
        return toDto(repository.save(new StudentDocument(student, name, path, file.getContentType())));
    }

    public byte[] download(Long id, Long studentId) throws IOException {
        StudentDocument document = repository.findByIdAndStudentId(id, studentId)
                .orElseThrow(() -> new IOException("Document not found"));
        return fileUploadService.getFileAsBytes(document.getFilePath());
    }

    public StudentDocumentDto find(Long id, Long studentId) {
        return repository.findByIdAndStudentId(id, studentId).map(this::toDto).orElse(null);
    }

    public boolean delete(Long id, Long studentId) {
        return repository.findByIdAndStudentId(id, studentId).map(document -> {
            fileUploadService.deleteProfileImage(document.getFilePath());
            repository.delete(document);
            return true;
        }).orElse(false);
    }

    private StudentDocumentDto toDto(StudentDocument document) {
        return new StudentDocumentDto(document.getId(), document.getName(), document.getContentType(), document.getUploadedAt(), document.getStatus());
    }
}