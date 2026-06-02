package com.sis.repository;

import com.sis.entity.CourseMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CourseMaterialRepository extends JpaRepository<CourseMaterial, Long> {
    List<CourseMaterial> findByCourseId(Long courseId);
}