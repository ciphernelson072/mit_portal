package com.sis.repository;

import com.sis.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface DepartmentRepository extends JpaRepository<Department, Long> {
    @Query("select department from Department department where department.faculty.id = :facultyId "
            + "and department.active = true and department.faculty.active = true "
            + "and department.faculty.campus.active = true order by lower(department.name)")
    List<Department> findByFacultyInActiveDirectoryOrderByNameAsc(
            @Param("facultyId") Long facultyId);

    @Query("select department from Department department "
            + "join fetch department.faculty faculty "
            + "join fetch faculty.campus campus "
            + "where department.active = true and faculty.active = true and campus.active = true "
            + "order by lower(department.name)")
    List<Department> findAllActiveInActiveDirectory();

    @Query("select case when count(department) > 0 then true else false end "
            + "from Department department where department.id = :id "
            + "and department.active = true "
            + "and department.faculty.active = true "
            + "and department.faculty.campus.active = true")
    boolean existsActiveInActiveFacultyAndCampus(@Param("id") Long id);
}