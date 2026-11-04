package com.sis.repository;

import com.sis.entity.HostelRoom;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface HostelRoomRepository extends JpaRepository<HostelRoom, Long> {
    List<HostelRoom> findByActiveTrueOrderByBuildingAscRoomCodeAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from HostelRoom room where room.id = :id and room.active = true")
    Optional<HostelRoom> findActiveByIdForUpdate(@Param("id") Long id);
}