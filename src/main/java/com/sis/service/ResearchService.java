package com.sis.service;

import com.sis.dto.*;
import com.sis.entity.*;
import com.sis.repository.ResearchProjectRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.ThesisSubmissionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import java.util.logging.Logger;

@Service
@Transactional
public class ResearchService {
    private static final Logger logger = Logger.getLogger(ResearchService.class.getName());

    private final ResearchProjectRepository projects;
    private final ThesisSubmissionRepository submissions;
    private final StudentRepository students;
    private final TeacherRepository teachers;
    private final FileUploadService fileUploadService;

    public ResearchService(ResearchProjectRepository projects, ThesisSubmissionRepository submissions,
                           StudentRepository students, TeacherRepository teachers, FileUploadService fileUploadService) {
        this.projects = projects;
        this.submissions = submissions;
        this.students = students;
        this.teachers = teachers;
        this.fileUploadService = fileUploadService;
    }

    public ResearchProject createProject(String username, CreateResearchProjectRequest request) {
        Long studentId = students.findByUserUsername(username).orElseThrow(() -> new IllegalArgumentException("Student profile not found")).getId();
        if (request.getSupervisorUserId() != null) {
            teachers.findByUserId(request.getSupervisorUserId())
                    .filter(teacher -> Boolean.TRUE.equals(teacher.getUser().getIsActive()))
                    .orElseThrow(() -> new IllegalArgumentException("Selected supervisor is not an active teacher"));
        }
        ResearchProject project = new ResearchProject();
        project.setStudentId(studentId);
        project.setTitle(request.getTitle());
        project.setAbstractText(request.getAbstractText());
        project.setSupervisorUserId(request.getSupervisorUserId());
        return projects.save(project);
    }

    @Transactional(readOnly = true)
    public List<ResearchProject> mine(String username) {
        Long studentId = students.findByUserUsername(username).orElseThrow(() -> new IllegalArgumentException("Student profile not found")).getId();
        return projects.findByStudentIdOrderByCreatedAtDesc(studentId);
    }

    @Transactional(readOnly = true)
    public List<ResearchSupervisorDto> availableSupervisors() {
        return teachers.findAllWithUser().stream()
                .filter(teacher -> Boolean.TRUE.equals(teacher.getUser().getIsActive()))
                .map(teacher -> new ResearchSupervisorDto(
                        teacher.getUser().getId(), teacher.getFullName(), teacher.getDepartment()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ResearchProject> pendingProjects() { return projects.findByStatusOrderByCreatedAtAsc(ResearchProjectStatus.PROPOSED); }

    public ResearchProject reviewProject(Long id, ReviewResearchProjectRequest request, String reviewer) {
        if (request.getStatus() != ResearchProjectStatus.APPROVED && request.getStatus() != ResearchProjectStatus.REJECTED) throw new IllegalArgumentException("Project review must be APPROVED or REJECTED");
        ResearchProject project = projects.findById(id).orElseThrow(() -> new IllegalArgumentException("Research project not found"));
        if (project.getStatus() != ResearchProjectStatus.PROPOSED) throw new IllegalStateException("Research project is already finalized");
        project.setStatus(request.getStatus());
        project.setReviewedBy(reviewer);
        project.setReviewedAt(LocalDateTime.now());
        return projects.save(project);
    }

    public ThesisSubmissionDto submitThesis(String username, Long researchProjectId, String notes,
                                             MultipartFile file) throws IOException {
        Long studentId = students.findByUserUsername(username).orElseThrow(() -> new IllegalArgumentException("Student profile not found")).getId();
        ResearchProject project = projects.findById(researchProjectId)
                .filter(item -> item.getStudentId().equals(studentId) && item.getStatus() == ResearchProjectStatus.APPROVED)
                .orElseThrow(() -> new IllegalStateException("Approved research project not found"));
        String filePath = fileUploadService.uploadThesisDocument(file, studentId);
        ThesisSubmission submission = new ThesisSubmission();
        submission.setResearchProjectId(project.getId());
        submission.setFilePath(filePath);
        submission.setNotes(notes);
        try {
            return toThesisDto(submissions.save(submission));
        } catch (RuntimeException e) {
            if (!fileUploadService.deleteProfileImage(filePath)) {
                logger.warning("Thesis record could not be saved and its uploaded file could not be removed: " + filePath);
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public List<ThesisSubmissionDto> mineTheses(String username) {
        Long studentId = students.findByUserUsername(username).orElseThrow(() -> new IllegalArgumentException("Student profile not found")).getId();
        List<Long> projectIds = projects.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(ResearchProject::getId).collect(Collectors.toList());
        if (projectIds.isEmpty()) return List.of();
        return submissions.findByResearchProjectIdInOrderBySubmittedAtDesc(projectIds).stream()
                .map(this::toThesisDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ThesisSubmissionDto> pendingTheses() {
        return submissions.findByStatusOrderBySubmittedAtAsc(ThesisSubmissionStatus.SUBMITTED).stream()
                .map(this::toThesisDto).collect(Collectors.toList());
    }

    public ThesisSubmissionDto reviewThesis(Long id, ReviewThesisSubmissionRequest request, String reviewer) {
        if (request.getStatus() != ThesisSubmissionStatus.APPROVED && request.getStatus() != ThesisSubmissionStatus.REVISION_REQUIRED && request.getStatus() != ThesisSubmissionStatus.REJECTED) throw new IllegalArgumentException("Invalid thesis review status");
        ThesisSubmission thesis = submissions.findById(id).orElseThrow(() -> new IllegalArgumentException("Thesis submission not found"));
        if (thesis.getStatus() == ThesisSubmissionStatus.APPROVED || thesis.getStatus() == ThesisSubmissionStatus.REJECTED) throw new IllegalStateException("Thesis submission is already finalized");
        thesis.setStatus(request.getStatus());
        thesis.setReviewedBy(reviewer);
        thesis.setReviewedAt(LocalDateTime.now());
        return toThesisDto(submissions.save(thesis));
    }

    @Transactional(readOnly = true)
    public byte[] downloadThesis(String username, Long id, boolean researchReviewer) throws IOException {
        ThesisSubmission thesis = submissions.findById(id).orElseThrow(() -> new FileNotFoundException("Thesis submission not found"));
        ResearchProject project = projects.findById(thesis.getResearchProjectId())
                .orElseThrow(() -> new FileNotFoundException("Research project not found"));
        if (!researchReviewer) {
            Long studentId = students.findByUserUsername(username)
                    .orElseThrow(() -> new AccessDeniedException("Student profile not found"))
                    .getId();
            if (!studentId.equals(project.getStudentId())) throw new AccessDeniedException("Thesis submission does not belong to this student");
        }
        return fileUploadService.getFileAsBytes(thesis.getFilePath());
    }

    private ThesisSubmissionDto toThesisDto(ThesisSubmission thesis) {
        return new ThesisSubmissionDto(thesis.getId(), thesis.getResearchProjectId(), thesis.getNotes(),
                thesis.getStatus(), thesis.getSubmittedAt());
    }
}