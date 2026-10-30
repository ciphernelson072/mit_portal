package com.sis.service;

import com.sis.dto.CreateScholarshipRequest;
import com.sis.dto.ScholarshipApplicationAdminDto;
import com.sis.entity.Scholarship;
import com.sis.entity.ScholarshipApplication;
import com.sis.entity.Student;
import com.sis.repository.ScholarshipApplicationRepository;
import com.sis.repository.ScholarshipRepository;
import com.sis.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ScholarshipService {
    private final ScholarshipRepository scholarships;
    private final ScholarshipApplicationRepository applications;
    private final StudentRepository students;

    public ScholarshipService(ScholarshipRepository scholarships,
                              ScholarshipApplicationRepository applications,
                              StudentRepository students) {
        this.scholarships = scholarships;
        this.applications = applications;
        this.students = students;
    }

    @Transactional
    public ScholarshipApplication apply(Long scholarshipId, Long studentId) {
        Scholarship scholarship = scholarships.findByIdForUpdate(scholarshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scholarship not found"));
        Optional<ScholarshipApplication> existing = applications.findByStudentIdAndScholarshipId(studentId, scholarshipId);
        if (existing.isPresent()) return existing.get();
        if (!scholarship.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Scholarship applications are closed");
        }
        return applications.save(new ScholarshipApplication(scholarshipId, studentId));
    }

    @Transactional(readOnly = true)
    public List<Scholarship> listForAdministration() {
        return scholarships.findAllByOrderByIdAsc();
    }

    @Transactional
    public Scholarship create(CreateScholarshipRequest request) {
        Scholarship scholarship = new Scholarship();
        scholarship.setName(request.getName().trim());
        scholarship.setDescription(request.getDescription() == null ? null : request.getDescription().trim());
        scholarship.setAmount(request.getAmount());
        scholarship.setOpen(true);
        return scholarships.save(scholarship);
    }

    @Transactional
    public Scholarship setAvailability(Long id, boolean open) {
        Scholarship scholarship = scholarships.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scholarship not found"));
        scholarship.setOpen(open);
        return scholarships.save(scholarship);
    }

    @Transactional(readOnly = true)
    public List<ScholarshipApplicationAdminDto> listApplications(Long scholarshipId) {
        List<ScholarshipApplication> records = scholarshipId == null
                ? applications.findAllByOrderByAppliedAtDesc()
                : applications.findByScholarshipIdOrderByAppliedAtAsc(scholarshipId);
        Map<Long, Student> studentById = students.findAllById(records.stream()
                        .map(ScholarshipApplication::getStudentId).distinct().collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Student::getId, student -> student));
        return records.stream().map(application -> {
            Student student = studentById.get(application.getStudentId());
            return new ScholarshipApplicationAdminDto(application.getId(), application.getScholarshipId(),
                    application.getStudentId(), student == null ? "Student profile unavailable" : student.getFullName(),
                    student == null ? null : student.getRegistrationNumber(), application.getStatus(),
                    application.getAppliedAt());
        }).collect(Collectors.toList());
    }

    @Transactional
    public ScholarshipApplication reviewApplication(Long id, String status) {
        ScholarshipApplication application = applications.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scholarship application not found"));
        if (!"SUBMITTED".equals(application.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Scholarship application is already finalized");
        }
        application.setStatus(status);
        return applications.save(application);
    }
}
