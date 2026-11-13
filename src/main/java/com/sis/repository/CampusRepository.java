package com.sis.repository;

import com.sis.entity.Campus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CampusRepository extends JpaRepository<Campus, Long> {
    List<Campus> findByActiveTrueOrderByNameAsc();
}