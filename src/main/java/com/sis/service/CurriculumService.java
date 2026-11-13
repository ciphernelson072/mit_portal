package com.sis.service;

import com.sis.dto.CreateCurriculumCourseRequest;
import com.sis.dto.CreatePrerequisiteRequest;
import com.sis.entity.*;
import com.sis.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;

@Service
@Transactional
public class CurriculumService {
    private final CurriculumCourseRepository curriculum;
    private final CoursePrerequisiteRepository prerequisites;
    private final ProgramRepository programs;
    private final CourseRepository courses;
    private final StudentRepository students;
    private final StudentProgramRepository studentPrograms;

    public CurriculumService(CurriculumCourseRepository curriculum, CoursePrerequisiteRepository prerequisites,
                             ProgramRepository programs, CourseRepository courses, StudentRepository students,
                             StudentProgramRepository studentPrograms) {
        this.curriculum = curriculum;
        this.prerequisites = prerequisites;
        this.programs = programs;
        this.courses = courses;
        this.students = students;
        this.studentPrograms = studentPrograms;
    }

    public CurriculumCourse addCourse(CreateCurriculumCourseRequest request) {
        CurriculumCourse item = new CurriculumCourse();
        item.setProgram(programs.findById(request.getProgramId()).orElseThrow(() -> new IllegalArgumentException("Program not found")));
        item.setCourse(courses.findById(request.getCourseId()).orElseThrow(() -> new IllegalArgumentException("Course not found")));
        item.setSemesterNumber(request.getSemesterNumber());
        item.setRequired(request.getRequired() == null || request.getRequired());
        CurriculumCourse saved = curriculum.save(item);
        return curriculum.findByIdWithProgramAndCourse(saved.getId())
                .orElseThrow(() -> new IllegalStateException("Saved curriculum course could not be reloaded"));
    }

    public CoursePrerequisite addPrerequisite(CreatePrerequisiteRequest request) {
        Course course = courses.findById(request.getCourseId()).orElseThrow(() -> new IllegalArgumentException("Course not found"));
        Course prerequisite = courses.findById(request.getPrerequisiteCourseId()).orElseThrow(() -> new IllegalArgumentException("Prerequisite course not found"));
        if (course.getId().equals(prerequisite.getId())) {
            throw new IllegalArgumentException("A course cannot require itself");
        }
        courses.findAllByIdInOrderByIdForUpdate(
                Arrays.asList(course.getId(), prerequisite.getId()));
        if (introducesPrerequisiteCycle(course.getId(), prerequisite.getId())) {
            throw new IllegalArgumentException("Course prerequisite would create a cycle");
        }
        CoursePrerequisite item = new CoursePrerequisite();
        item.setCourse(course);
        item.setPrerequisiteCourse(prerequisite);
        CoursePrerequisite saved = prerequisites.save(item);
        return prerequisites.findByIdWithCourses(saved.getId())
                .orElseThrow(() -> new IllegalStateException("Saved course prerequisite could not be reloaded"));
    }

    private boolean introducesPrerequisiteCycle(Long courseId, Long prerequisiteCourseId) {
        ArrayDeque<Long> pendingCourses = new ArrayDeque<>();
        Set<Long> visitedCourses = new HashSet<>();
        pendingCourses.add(prerequisiteCourseId);
        while (!pendingCourses.isEmpty()) {
            Long currentCourseId = pendingCourses.removeFirst();
            if (courseId.equals(currentCourseId)) return true;
            if (visitedCourses.add(currentCourseId)) {
                pendingCourses.addAll(prerequisites.findPrerequisiteCourseIdsByCourseId(currentCourseId));
            }
        }
        return false;
    }

    @Transactional(readOnly = true)
    public List<CurriculumCourse> programCurriculum(Long programId) {
        return curriculum.findByProgramIdOrderBySemesterNumberAscCourseCourseCodeAsc(programId);
    }

    @Transactional(readOnly = true)
    public Long studentProgramId(String username) {
        Student student = students.findByUserUsername(username).orElseThrow(() -> new IllegalArgumentException("Student profile not found"));
        return studentPrograms.findByStudentIdOrderByStartDateDesc(student.getId()).stream()
                .filter(item -> item.getStatus() == StudentLifecycleStatus.ACTIVE)
                .findFirst()
                .map(item -> item.getProgram().getId())
                .orElseThrow(() -> new IllegalStateException("Student has no active program"));
    }

    @Transactional(readOnly = true)
    public List<CoursePrerequisite> prerequisites(Long courseId) {
        return prerequisites.findByCourseId(courseId);
    }
}