package com.sis.repository;
import com.sis.entity.CampusEvent;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import javax.persistence.LockModeType;
import java.util.Optional;

public interface CampusEventRepository extends JpaRepository<CampusEvent, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select event from CampusEvent event where event.id = :id")
    Optional<CampusEvent> findByIdForUpdate(@Param("id") Long id);
}
