package com.sis.service;

import com.sis.entity.Student;
import com.sis.entity.TransportPass;
import com.sis.repository.StudentRepository;
import com.sis.repository.TransportPassRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class CampusTransportService {
    private final StudentRepository students;
    private final TransportPassRepository passes;

    public CampusTransportService(StudentRepository students, TransportPassRepository passes) {
        this.students = students;
        this.passes = passes;
    }

    @Transactional
    public Optional<TransportPass> latestPass(String username) {
        Student student = students.findByUserUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));
        Optional<TransportPass> latest = passes.findFirstByStudentIdOrderByIdDesc(student.getId());
        latest.filter(pass -> "ACTIVE".equals(pass.getStatus()) && pass.getValidUntil() != null
                        && pass.getValidUntil().isBefore(LocalDate.now()))
                .ifPresent(pass -> {
                    pass.setStatus("EXPIRED");
                    passes.save(pass);
                });
        return latest;
    }

    @Transactional
    public TransportPass renew(String username) {
        Student student = students.findByUserUsernameForUpdate(username)
                .orElseThrow(() -> new IllegalArgumentException("Student profile not found"));
        LocalDate today = LocalDate.now();
        passes.findByStudentIdAndStatusAndValidUntilBefore(student.getId(), "ACTIVE", today)
                .forEach(expired -> {
                    expired.setStatus("EXPIRED");
                    passes.save(expired);
                });
        if (passes.existsByStudentIdAndStatusAndValidUntilGreaterThanEqual(student.getId(), "ACTIVE", today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An active transport pass is already valid");
        }
        return passes.save(new TransportPass(student.getId(), today.plusMonths(6)));
    }
}
