package com.sis.repository;

import com.sis.entity.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    @Query("select faculty from Faculty faculty where faculty.campus.id = :campusId "
            + "and faculty.active = true and faculty.campus.active = true order by lower(faculty.name)")
    List<Faculty> findByCampusIdInActiveCampusOrderByNameAsc(
            @Param("campusId") Long campusId);

    @Query("select case when count(faculty) > 0 then true else false end "
            + "from Faculty faculty where faculty.id = :id "
            + "and faculty.active = true and faculty.campus.active = true")
    boolean existsActiveInActiveCampus(@Param("id") Long id);
}