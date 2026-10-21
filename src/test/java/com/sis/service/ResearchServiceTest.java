package com.sis.service;

import com.sis.dto.CreateResearchProjectRequest;
import com.sis.dto.ThesisSubmissionDto;
import com.sis.entity.*;
import com.sis.repository.ResearchProjectRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.ThesisSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockMultipartFile;

import java.io.FileNotFoundException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResearchServiceTest {
    private ResearchProjectRepository projects;
    private ThesisSubmissionRepository submissions;
    private StudentRepository students;
    private TeacherRepository teachers;
    private FileUploadService files;
    private ResearchService service;

    @BeforeEach
    void setUp() {
        projects = mock(ResearchProjectRepository.class);
        submissions = mock(ThesisSubmissionRepository.class);
        students = mock(StudentRepository.class);
        teachers = mock(TeacherRepository.class);
        files = mock(FileUploadService.class);
        service = new ResearchService(projects, submissions, students, teachers, files);
    }

    @Test
    void proposalOnlyAcceptsAnActiveTeacherAsSupervisor() {
        Student student = student("student1", 7L);
        when(students.findByUserUsername("student1")).thenReturn(Optional.of(student));
        when(teachers.findByUserId(22L)).thenReturn(Optional.empty());
        CreateResearchProjectRequest request = new CreateResearchProjectRequest();
        request.setTitle("Research topic");
        request.setSupervisorUserId(22L);

        assertThrows(IllegalArgumentException.class, () -> service.createProject("student1", request));
        verify(projects, never()).save(any());
    }

    @Test
    void thesisSubmissionRequiresAnApprovedProjectOwnedByTheStudent() throws Exception {
        Student student = student("student1", 7L);
        ResearchProject project = project(11L, 8L, ResearchProjectStatus.APPROVED);
        when(students.findByUserUsername("student1")).thenReturn(Optional.of(student));
        when(projects.findById(11L)).thenReturn(Optional.of(project));
        MockMultipartFile file = new MockMultipartFile("file", "thesis.pdf", "application/pdf", "%PDF-1.7".getBytes());

        assertThrows(IllegalStateException.class, () -> service.submitThesis("student1", 11L, null, file));
        verify(files, never()).uploadThesisDocument(any(), anyLong());
    }

    @Test
    void savedThesisResponseDoesNotExposeItsStoragePath() throws Exception {
        Student student = student("student1", 7L);
        ResearchProject project = project(11L, 7L, ResearchProjectStatus.APPROVED);
        when(students.findByUserUsername("student1")).thenReturn(Optional.of(student));
        when(projects.findById(11L)).thenReturn(Optional.of(project));
        when(files.uploadThesisDocument(any(), eq(7L))).thenReturn("research/7/generated.pdf");
        when(submissions.save(any(ThesisSubmission.class))).thenAnswer(invocation -> {
            ThesisSubmission saved = invocation.getArgument(0);
            saved.setId(31L);
            return saved;
        });
        MockMultipartFile file = new MockMultipartFile("file", "thesis.pdf", "application/pdf", "%PDF-1.7".getBytes());

        ThesisSubmissionDto result = service.submitThesis("student1", 11L, "For review", file);

        assertEquals(31L, result.getId());
        assertEquals(11L, result.getResearchProjectId());
        assertEquals(ThesisSubmissionStatus.SUBMITTED, result.getStatus());
        assertFalse(result.toString().contains("research/7"));
    }

    @Test
    void studentCanDownloadOnlyTheirOwnThesisWhileResearchReviewersCanReview() throws Exception {
        ThesisSubmission thesis = new ThesisSubmission();
        thesis.setId(31L);
        thesis.setResearchProjectId(11L);
        thesis.setFilePath("research/7/generated.pdf");
        when(submissions.findById(31L)).thenReturn(Optional.of(thesis));
        when(projects.findById(11L)).thenReturn(Optional.of(project(11L, 7L, ResearchProjectStatus.APPROVED)));
        when(students.findByUserUsername("student1")).thenReturn(Optional.of(student("student1", 7L)));
        when(students.findByUserUsername("student2")).thenReturn(Optional.of(student("student2", 8L)));
        when(files.getFileAsBytes("research/7/generated.pdf")).thenReturn(new byte[]{1, 2, 3});

        assertArrayEquals(new byte[]{1, 2, 3}, service.downloadThesis("student1", 31L, false));
        assertThrows(AccessDeniedException.class, () -> service.downloadThesis("student2", 31L, false));
        assertArrayEquals(new byte[]{1, 2, 3}, service.downloadThesis("reviewer", 31L, true));
    }

    @Test
    void missingThesisDownloadIsReportedAsNotFound() {
        when(submissions.findById(99L)).thenReturn(Optional.empty());

        assertThrows(FileNotFoundException.class, () -> service.downloadThesis("student1", 99L, false));
    }

    private Student student(String username, Long id) {
        User user = new User();
        user.setUsername(username);
        Student student = new Student();
        student.setId(id);
        student.setUser(user);
        return student;
    }

    private ResearchProject project(Long id, Long studentId, ResearchProjectStatus status) {
        ResearchProject project = new ResearchProject();
        project.setId(id);
        project.setStudentId(studentId);
        project.setStatus(status);
        return project;
    }
}
