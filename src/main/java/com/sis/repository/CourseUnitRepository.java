package com.sis.repository;

import com.sis.entity.CourseUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CourseUnitRepository extends JpaRepository<CourseUnit, Long> {
    @Query("select distinct u from CourseUnit u join fetch u.course c join fetch c.category join fetch u.teacher where u.teacher.id = :teacherId order by c.name, u.name")
    List<CourseUnit> findByTeacherIdWithCourse(Long teacherId);

    @Query("select distinct u from CourseUnit u join fetch u.course c join fetch c.category join fetch u.teacher join u.students s where s.id = :studentId order by c.name, u.name")
    List<CourseUnit> findByStudentIdWithCourse(Long studentId);

    @Query("select distinct u from CourseUnit u join fetch u.course c join fetch c.category join fetch u.teacher order by c.name, u.name")
    List<CourseUnit> findAllWithCourse();

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM CourseUnit u "
            + "WHERE u.id = :unitId AND u.teacher.user.username = :username "
            + "AND u.course.teacher.user.username = :username")
    boolean existsAssignedToTeacherForCourse(@Param("unitId") Long unitId,
                                             @Param("username") String username);

    boolean existsByUnitCodeIgnoreCase(String unitCode);
}