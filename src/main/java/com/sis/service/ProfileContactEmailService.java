package com.sis.service;

import com.sis.entity.Student;
import com.sis.entity.Teacher;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ProfileContactEmailService {
    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;

    public ProfileContactEmailService(StudentRepository studentRepository, TeacherRepository teacherRepository) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
    }

    public Map<Long, String> findForUsers(Collection<Long> userIds) {
        Map<Long, String> profileEmails = new LinkedHashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return profileEmails;
        }
        for (Student student : studentRepository.findAllWithUserByUserIdIn(userIds)) {
            if (Boolean.TRUE.equals(student.getUser().getIsActive()) && isValidEmail(student.getEmail())) {
                profileEmails.putIfAbsent(student.getUser().getId(), student.getEmail().trim());
            }
        }
        for (Teacher teacher : teacherRepository.findAllWithUserByUserIdIn(userIds)) {
            if (Boolean.TRUE.equals(teacher.getUser().getIsActive()) && isValidEmail(teacher.getEmail())) {
                profileEmails.putIfAbsent(teacher.getUser().getId(), teacher.getEmail().trim());
            }
        }
        return profileEmails;
    }

    public static boolean isValidEmail(String email) {
        if (email == null || email.isBlank() || email.length() > 254) {
            return false;
        }
        try {
            InternetAddress address = new InternetAddress(email.trim(), true);
            address.validate();
            return address.getAddress().equals(email.trim());
        } catch (AddressException ex) {
            return false;
        }
    }
}
