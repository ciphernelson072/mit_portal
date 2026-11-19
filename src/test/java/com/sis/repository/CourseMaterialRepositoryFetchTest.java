package com.sis.repository;

import com.sis.entity.Course;
import com.sis.entity.CourseCategory;
import com.sis.entity.CourseMaterial;
import com.sis.entity.Role;
import com.sis.entity.Teacher;
import com.sis.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManagerFactory;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.jpa.open-in-view=false",
        "sis.sample-data.enabled=false"
})
class CourseMaterialRepositoryFetchTest {

    @Autowired
    private CourseMaterialRepository materials;

    @Autowired
    private CourseCategoryRepository categories;

    @Autowired
    private TeacherRepository teachers;

    @Autowired
    private CourseRepository courses;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void materialListingFetchesUploaderAndAllowsLegacyNullUploader() {
        Teacher teacher = teachers.saveAndFlush(
                new Teacher(new User("material-teacher", "encoded", Role.TEACHER), "Material Teacher"));
        CourseCategory category = categories.saveAndFlush(new CourseCategory("MAT", "Materials"));
        Course course = new Course("Material Course", teacher);
        course.setCourseCode("MAT101");
        course.setCategory(category);
        course = courses.saveAndFlush(course);

        User uploader = users.saveAndFlush(new User("material-uploader", "encoded", Role.ADMIN));
        materials.saveAndFlush(new CourseMaterial(course, "Uploaded material", "", "uploaded.pdf", uploader));
        materials.saveAndFlush(new CourseMaterial(course, "Legacy material", "", "legacy.pdf", null));

        List<CourseMaterial> results = materials.findByCourseId(course.getId());
        assertThat(results).hasSize(2);
        assertThat(results).filteredOn(material -> "Uploaded material".equals(material.getTitle()))
                .allSatisfy(material -> {
                    assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(material, "uploadedBy")).isTrue();
                    assertThat(material.getUploadedBy().getUsername()).isEqualTo("material-uploader");
                });
        assertThat(results).filteredOn(material -> "Legacy material".equals(material.getTitle()))
                .allSatisfy(material -> {
                    assertThat(entityManagerFactory.getPersistenceUnitUtil().isLoaded(material, "uploadedBy")).isTrue();
                    assertThat(material.getUploadedBy()).isNull();
                });
    }
}
