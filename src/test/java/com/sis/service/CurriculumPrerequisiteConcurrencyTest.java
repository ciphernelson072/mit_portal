package com.sis.service;

import com.sis.dto.CreatePrerequisiteRequest;
import com.sis.entity.Course;
import com.sis.entity.CourseCategory;
import com.sis.entity.Role;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import com.sis.repository.CourseCategoryRepository;
import com.sis.repository.CoursePrerequisiteRepository;
import com.sis.repository.CourseRepository;
import com.sis.repository.CurriculumCourseRepository;
import com.sis.repository.ProgramRepository;
import com.sis.repository.StudentProgramRepository;
import com.sis.repository.StudentRepository;
import com.sis.repository.TeacherRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "sis.sample-data.enabled=false")
@Import(CurriculumService.class)
class CurriculumPrerequisiteConcurrencyTest {

    @Autowired
    private CurriculumService curriculumService;

    @Autowired
    private CourseRepository courses;

    @Autowired
    private CourseCategoryRepository categories;

    @Autowired
    private TeacherRepository teachers;

    @Autowired
    private CoursePrerequisiteRepository prerequisites;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentPrerequisiteChangesCannotCreateCycle() throws Exception {
        Teacher teacher = teachers.saveAndFlush(new Teacher(
                new User("curriculum-race-teacher", "encoded", Role.TEACHER), "Curriculum Race Teacher"));
        CourseCategory category = categories.saveAndFlush(new CourseCategory("CYCLE", "Cycle Category"));
        Course firstCourse = saveCourse("CYCLE101", "Cycle Course One", teacher, category);
        Course secondCourse = saveCourse("CYCLE102", "Cycle Course Two", teacher, category);
        Long firstCourseId = firstCourse.getId();
        Long secondCourseId = secondCourse.getId();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> addWhenReleased(
                    ready, start, firstCourseId, secondCourseId));
            Future<Boolean> second = executor.submit(() -> addWhenReleased(
                    ready, start, secondCourseId, firstCourseId));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            int insertedEdges = (first.get(20, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(20, TimeUnit.SECONDS) ? 1 : 0);

            assertThat(insertedEdges).isEqualTo(1);
            assertThat(prerequisites.findPrerequisiteCourseIdsByCourseId(firstCourseId).size()
                    + prerequisites.findPrerequisiteCourseIdsByCourseId(secondCourseId).size()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private Course saveCourse(String code, String name, Teacher teacher, CourseCategory category) {
        Course course = new Course(name, teacher);
        course.setCourseCode(code);
        course.setCategory(category);
        return courses.saveAndFlush(course);
    }

    private boolean addWhenReleased(CountDownLatch ready, CountDownLatch start,
                                    Long courseId, Long prerequisiteCourseId) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Prerequisite concurrency test did not start");
        }
        CreatePrerequisiteRequest request = new CreatePrerequisiteRequest();
        request.setCourseId(courseId);
        request.setPrerequisiteCourseId(prerequisiteCourseId);
        try {
            curriculumService.addPrerequisite(request);
            return true;
        } catch (IllegalArgumentException expectedCycleConflict) {
            return false;
        }
    }
}
