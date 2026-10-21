package com.sis.service;

import com.sis.dto.CourseMaterialDto;
import com.sis.entity.Course;
import com.sis.entity.CourseMaterial;
import com.sis.entity.User;
import com.sis.repository.CourseMaterialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Course materials - split out of the former monolithic SchoolService. */
@Service
@Transactional
public class CourseMaterialService {

    private final CourseMaterialRepository courseMaterialRepository;

    public CourseMaterialService(CourseMaterialRepository courseMaterialRepository) {
        this.courseMaterialRepository = courseMaterialRepository;
    }

    public List<CourseMaterialDto> getMaterialsForCourse(Long courseId) {
        if (courseId == null) return Collections.emptyList();
        return courseMaterialRepository.findByCourseId(courseId).stream()
                .map(mat -> new CourseMaterialDto(
                        mat.getId(),
                        mat.getTitle(),
                        mat.getDescription(),
                        mat.getFileUrl(),
                        mat.getUploadedAt(),
                        mat.getUploadedBy() == null ? null : mat.getUploadedBy().getUsername()))
                .collect(Collectors.toList());
    }

    public CourseMaterialDto createMaterial(Course course, String title, String description, String fileUrl, User uploadedBy) {
        CourseMaterial saved = courseMaterialRepository.save(new CourseMaterial(course, title, description, fileUrl, uploadedBy));
        return new CourseMaterialDto(saved.getId(), saved.getTitle(), saved.getDescription(), saved.getFileUrl(),
                saved.getUploadedAt(), saved.getUploadedBy() == null ? null : saved.getUploadedBy().getUsername());
    }
}
