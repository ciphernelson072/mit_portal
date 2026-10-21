package com.sis.repository;

import com.sis.entity.LibraryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LibraryItemRepository extends JpaRepository<LibraryItem, Long> {
    List<LibraryItem> findByActiveTrueOrderByTitleAsc();
}