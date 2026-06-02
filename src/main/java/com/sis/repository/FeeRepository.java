package com.sis.repository;

import com.sis.entity.Fee;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeeRepository extends JpaRepository<Fee, Long> {
    List<Fee> findByStudentId(Long studentId);
    long countByPaid(boolean paid);
}