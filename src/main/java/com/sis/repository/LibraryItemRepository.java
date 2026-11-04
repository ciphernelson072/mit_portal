package com.sis.repository;

import com.sis.entity.LibraryItem;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface LibraryItemRepository extends JpaRepository<LibraryItem, Long> {
    List<LibraryItem> findByActiveTrueOrderByTitleAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from LibraryItem item where item.id = :id and item.active = true")
    Optional<LibraryItem> findActiveByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select item from LibraryItem item where item.id = :id")
    Optional<LibraryItem> findByIdForUpdate(@Param("id") Long id);
}