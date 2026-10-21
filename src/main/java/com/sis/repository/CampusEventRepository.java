package com.sis.repository;
import com.sis.entity.CampusEvent;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CampusEventRepository extends JpaRepository<CampusEvent, Long> {}
