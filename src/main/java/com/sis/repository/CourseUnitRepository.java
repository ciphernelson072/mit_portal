package com.sis.repository;

import com.sis.entity.CourseUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CourseUnitRepository extends JpaRepository<CourseUnit, Long> {
    @Query("select distinct u from CourseUnit u join fetch u.course c join fetch c.category join fetch u.teacher where u.teacher.id = :teacherId order by c.name, u.name")
    List<CourseUnit> findByTeacherIdWithCourse(Long teacherId);

    @Query("select distinct unit from CourseUnit unit "
            + "join fetch unit.course course join fetch course.category join fetch unit.teacher "
            + "where exists (select assigned.id from CourseUnit assignedUnit "
            + "join assignedUnit.students assigned "
            + "where assignedUnit.id = unit.id and assigned.id = :studentId) "
            + "or exists (select registration.id from CourseRegistration registration "
            + "where registration.student.id = :studentId "
            + "and registration.section.course.id = unit.course.id "
            + "and registration.status = com.sis.entity.RegistrationStatus.REGISTERED) "
            + "order by course.name, unit.name")
    List<CourseUnit> findByStudentIdWithCourse(@Param("studentId") Long studentId);

    @Query("select distinct u from CourseUnit u join fetch u.course c join fetch c.category join fetch u.teacher order by c.name, u.name")
    List<CourseUnit> findAllWithCourse();

    @Query("SELECT CASE WHEN COUNT(u) > 0 THEN true ELSE false END FROM CourseUnit u "
            + "WHERE u.id = :unitId AND u.teacher.user.username = :username")
    boolean existsAssignedToTeacherForCourse(@Param("unitId") Long unitId,
                                             @Param("username") String username);

    @Query("select distinct student from Student student join fetch student.user "
            + "where exists (select unit.id from CourseUnit unit join unit.students assigned "
            + "where unit.id = :unitId and assigned.id = student.id) "
            + "or exists (select registration.id from CourseRegistration registration "
            + "where registration.student.id = student.id "
            + "and registration.section.course.id = "
            + "(select unit.course.id from CourseUnit unit where unit.id = :unitId) "
            + "and registration.status = com.sis.entity.RegistrationStatus.REGISTERED) "
            + "order by student.fullName")
    List<com.sis.entity.Student> findRosterStudentsByUnitId(@Param("unitId") Long unitId);

    @Query("select case when count(unit.id) > 0 then true else false end from CourseUnit unit "
            + "where unit.teacher.user.username = :username "
            + "and (exists (select assigned.id from CourseUnit assignedUnit "
            + "join assignedUnit.students assigned where assignedUnit.id = unit.id "
            + "and assigned.id = :studentId) "
            + "or exists (select registration.id from CourseRegistration registration "
            + "where registration.student.id = :studentId "
            + "and registration.section.course.id = unit.course.id "
            + "and registration.status = com.sis.entity.RegistrationStatus.REGISTERED))")
    boolean existsRosterStudentForTeacher(@Param("username") String username,
                                          @Param("studentId") Long studentId);

    boolean existsByUnitCodeIgnoreCase(String unitCode);
}