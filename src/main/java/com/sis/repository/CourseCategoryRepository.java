package com.sis.repository;

import com.sis.entity.CourseCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseCategoryRepository extends JpaRepository<CourseCategory, Long> {
    List<CourseCategory> findByActiveTrueOrderByNameAsc();
    Optional<CourseCategory> findByCodeIgnoreCase(String code);
    Optional<CourseCategory> findByNameIgnoreCase(String name);
}