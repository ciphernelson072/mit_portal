package com.sis.repository;

import com.sis.entity.Student;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import javax.persistence.LockModeType;

public interface StudentRepository extends JpaRepository<Student, Long> {
    Optional<Student> findByUserId(Long userId);
    Optional<Student> findByUserUsername(String username);
    boolean existsByProfileImagePath(String profileImagePath);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Student s WHERE s.user.username = :username")
    Optional<Student> findByUserUsernameForUpdate(@Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Student s WHERE s.id = :studentId")
    Optional<Student> findByIdForUpdate(@Param("studentId") Long studentId);

    @EntityGraph(attributePaths = "user")
    @Query("SELECT s FROM Student s WHERE s.id = :id")
    Optional<Student> findByIdWithUser(Long id);

    long countByUserIsActiveTrue();

    @Query("SELECT DISTINCT s FROM Student s LEFT JOIN FETCH s.courses WHERE s.user.username = :username")
    Optional<Student> findByUserUsernameWithCourses(@Param("username") String username);

    @Query("SELECT s.user.id FROM Student s JOIN s.courses c WHERE c.id = :courseId")
    List<Long> findUserIdsByCourseId(@Param("courseId") Long courseId);

    @Query("select distinct student from Student student join fetch student.user "
            + "where exists (select course.id from Course course join course.students enrolled "
            + "where course.id = :courseId and enrolled.id = student.id) "
            + "or exists (select registration.id from CourseRegistration registration "
            + "where registration.student.id = student.id and registration.section.course.id = :courseId "
            + "and registration.status = com.sis.entity.RegistrationStatus.REGISTERED) "
            + "order by student.fullName")
    List<Student> findByCourseIdIncludingRegistrations(@Param("courseId") Long courseId);

    @Query("SELECT s.user.id FROM Student s WHERE s.id = :studentId")
    Optional<Long> findUserIdByStudentId(@Param("studentId") Long studentId);

    List<Student> findByClassName(String className);

    /**
     * JOIN FETCH the linked User in the same query, instead of the default lazy
     * per-row load - every caller here (AdminService.toStudentDto, etc.) reads
     * student.getUser().getUsername() for every row, which without this would be
     * N+1 (1 query for the student list, then 1 more per student for its user).
     */
    @Query(value = "SELECT s FROM Student s JOIN FETCH s.user",
           countQuery = "SELECT COUNT(s) FROM Student s")
    Page<Student> findAllWithUser(Pageable pageable);

    @Query(value = "SELECT s FROM Student s JOIN FETCH s.user "
            + "WHERE (:query = '' OR LOWER(s.fullName) LIKE CONCAT('%', :query, '%') "
            + "OR LOWER(COALESCE(s.registrationNumber, '')) LIKE CONCAT('%', :query, '%') "
            + "OR LOWER(COALESCE(s.email, '')) LIKE CONCAT('%', :query, '%') "
            + "OR LOWER(s.user.username) LIKE CONCAT('%', :query, '%')) "
            + "AND (:active IS NULL OR s.user.isActive = :active)",
           countQuery = "SELECT COUNT(s) FROM Student s JOIN s.user "
                   + "WHERE (:query = '' OR LOWER(s.fullName) LIKE CONCAT('%', :query, '%') "
                   + "OR LOWER(COALESCE(s.registrationNumber, '')) LIKE CONCAT('%', :query, '%') "
                   + "OR LOWER(COALESCE(s.email, '')) LIKE CONCAT('%', :query, '%') "
                   + "OR LOWER(s.user.username) LIKE CONCAT('%', :query, '%')) "
                   + "AND (:active IS NULL OR s.user.isActive = :active)")
    Page<Student> searchWithUser(@Param("query") String query, @Param("active") Boolean active, Pageable pageable);

    @Query("SELECT s FROM Student s JOIN FETCH s.user")
    List<Student> findAllWithUser();

    @Query("SELECT s FROM Student s JOIN FETCH s.user WHERE s.user.id IN :userIds")
    List<Student> findAllWithUserByUserIdIn(@Param("userIds") Collection<Long> userIds);

    @Query("SELECT s FROM Student s JOIN FETCH s.user WHERE LOWER(TRIM(s.email)) = LOWER(TRIM(:email)) "
            + "AND s.user.isActive = true ORDER BY s.id")
    List<Student> findActiveByEmailIgnoreCaseWithUser(@Param("email") String email);

    /** Fetches the student's courses AND each course's teacher in one query, avoiding
     *  the N+1 that course.getTeacher() would otherwise cause per course. */
    @Query("SELECT DISTINCT s FROM Student s LEFT JOIN FETCH s.courses c LEFT JOIN FETCH c.teacher WHERE s.id = :studentId")
    Optional<Student> findByIdWithCoursesAndTeachers(Long studentId);
}
