package com.sis.controller;

import com.sis.entity.Student;
import com.sis.dto.AdminFileListingDto;
import com.sis.service.FileUploadService;
import com.sis.service.StudentService;
import com.sis.service.TeacherService;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import com.sis.repository.StudentDocumentRepository;
import com.sis.repository.ThesisSubmissionRepository;
import com.sis.repository.CourseMaterialRepository;
import com.sis.repository.AccreditationRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.http.HttpStatus;

class FileUploadControllerTest {

    @Test
    void adminFileListingDelegatesToUploadService() throws IOException {
        FileUploadService files = mock(FileUploadService.class);
        AdminFileListingDto listing = new AdminFileListingDto(java.util.List.of(), false);
        when(files.listStoredFiles()).thenReturn(listing);
        FileUploadController controller = new FileUploadController();
        ReflectionTestUtils.setField(controller, "fileUploadService", files);

        assertEquals(listing, controller.listStoredFiles().getBody());
        verify(files).listStoredFiles();
    }

    @Test
    void deleteRejectsFilesStillReferencedByAStudentProfile() {
        FileUploadService files = mock(FileUploadService.class);
        StudentRepository students = mock(StudentRepository.class);
        when(files.normalizeRelativePath("students/17/profile.png")).thenReturn("students/17/profile.png");
        when(students.existsByProfileImagePath("students/17/profile.png")).thenReturn(true);
        FileUploadController controller = new FileUploadController();
        ReflectionTestUtils.setField(controller, "fileUploadService", files);
        ReflectionTestUtils.setField(controller, "studentRepository", students);

        var response = controller.deleteFile("students/17/profile.png");

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().toString().contains("still referenced"));
        verify(files, never()).deleteProfileImage(anyString());
    }

    @Test
    void deleteAllowsAnUnreferencedFile() {
        FileUploadService files = mock(FileUploadService.class);
        when(files.normalizeRelativePath("tmp/orphan.txt")).thenReturn("tmp/orphan.txt");
        when(files.deleteProfileImage("tmp/orphan.txt")).thenReturn(true);
        FileUploadController controller = new FileUploadController();
        ReflectionTestUtils.setField(controller, "fileUploadService", files);
        ReflectionTestUtils.setField(controller, "studentRepository", mock(StudentRepository.class));
        ReflectionTestUtils.setField(controller, "teacherRepository", mock(TeacherRepository.class));
        ReflectionTestUtils.setField(controller, "studentDocumentRepository", mock(StudentDocumentRepository.class));
        ReflectionTestUtils.setField(controller, "thesisSubmissionRepository", mock(ThesisSubmissionRepository.class));
        ReflectionTestUtils.setField(controller, "courseMaterialRepository", mock(CourseMaterialRepository.class));
        ReflectionTestUtils.setField(controller, "accreditationRecordRepository", mock(AccreditationRecordRepository.class));

        var response = controller.deleteFile("tmp/orphan.txt");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(files).deleteProfileImage("tmp/orphan.txt");
    }

    @Test
    void failedStudentImageUploadKeepsPreviousImage() throws IOException {
        FileUploadService files = mock(FileUploadService.class);
        StudentService students = mock(StudentService.class);
        Student student = new Student();
        student.setProfileImagePath("students/17/old.png");
        when(students.getStudentById(17L)).thenReturn(Optional.of(student));
        MockMultipartFile file = new MockMultipartFile("file", "new.png", "image/png", new byte[]{1});
        when(files.uploadStudentProfileImage(file, 17L)).thenThrow(new IOException("Disk is unavailable"));

        FileUploadController controller = new FileUploadController();
        ReflectionTestUtils.setField(controller, "fileUploadService", files);
        ReflectionTestUtils.setField(controller, "studentService", students);
        ReflectionTestUtils.setField(controller, "teacherService", mock(TeacherService.class));

        assertEquals(400, controller.uploadStudentProfileImage(17L, file).getStatusCodeValue());
        assertEquals("students/17/old.png", student.getProfileImagePath());
        verify(files, never()).deleteProfileImage("students/17/old.png");
        verify(students, never()).updateStudent(eq(17L), any(Student.class));
    }
}
