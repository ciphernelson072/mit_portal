package com.sis.repository;

import com.sis.entity.Program;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ProgramRepository extends JpaRepository<Program, Long> {
    @Query("select program from Program program where program.department.id = :departmentId "
            + "and program.active = true and program.department.active = true "
            + "and program.department.faculty.active = true "
            + "and program.department.faculty.campus.active = true "
            + "order by lower(program.name)")
    List<Program> findByDepartmentIdAndActiveDirectoryOrderByNameAsc(
            @org.springframework.data.repository.query.Param("departmentId") Long departmentId);

    @EntityGraph(attributePaths = {"department.faculty.campus"})
    @Query("select program from Program program where program.id = :id")
    Optional<Program> findByIdWithDirectory(@org.springframework.data.repository.query.Param("id") Long id);

    @EntityGraph(attributePaths = {"department"})
    @Query("select program from Program program "
            + "where program.active = true and program.department.active = true "
            + "and program.department.faculty.active = true "
            + "and program.department.faculty.campus.active = true "
            + "order by lower(program.name)")
    List<Program> findActiveProgramsInActiveDirectoryOrderByName();

    @Query("select case when count(program) > 0 then true else false end "
            + "from Program program where program.id = :programId "
            + "and program.active = true and program.department.active = true "
            + "and program.department.faculty.active = true "
            + "and program.department.faculty.campus.active = true")
    boolean existsActiveInDirectory(
            @org.springframework.data.repository.query.Param("programId") Long programId);
}