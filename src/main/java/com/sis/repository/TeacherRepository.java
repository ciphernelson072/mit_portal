package com.sis.repository;

import com.sis.entity.Teacher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.repository.query.Param;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {
    Optional<Teacher> findByUserId(Long userId);
    boolean existsByProfileImagePath(String profileImagePath);

    Optional<Teacher> findByUserUsername(String username);
    long countByUserIsActiveTrue();

    /** See StudentRepository#findAllWithUser - same N+1 rationale. */
    @Query(value = "SELECT t FROM Teacher t JOIN FETCH t.user "
            + "LEFT JOIN FETCH t.homeDepartment department "
            + "LEFT JOIN FETCH department.faculty faculty "
            + "LEFT JOIN FETCH faculty.campus",
           countQuery = "SELECT COUNT(t) FROM Teacher t")
    Page<Teacher> findAllWithUser(Pageable pageable);

    @Query("SELECT t FROM Teacher t JOIN FETCH t.user "
            + "LEFT JOIN FETCH t.homeDepartment department "
            + "LEFT JOIN FETCH department.faculty faculty "
            + "LEFT JOIN FETCH faculty.campus")
    List<Teacher> findAllWithUser();

    @Query("SELECT t FROM Teacher t JOIN FETCH t.user WHERE t.user.id IN :userIds")
    List<Teacher> findAllWithUserByUserIdIn(@Param("userIds") Collection<Long> userIds);

}
