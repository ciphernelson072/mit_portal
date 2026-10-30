package com.sis.repository;

import com.sis.entity.Staff;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface StaffRepository extends JpaRepository<Staff, Long> {
    boolean existsByUser_Id(Long userId);
    boolean existsByEmployeeNumber(String employeeNumber);

    @EntityGraph(attributePaths = "user")
    List<Staff> findAll();

    @EntityGraph(attributePaths = "user")
    Optional<Staff> findByUserUsername(String username);
}