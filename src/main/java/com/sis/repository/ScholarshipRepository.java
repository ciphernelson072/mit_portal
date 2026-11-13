package com.sis.repository;
import com.sis.entity.Scholarship;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;

public interface ScholarshipRepository extends JpaRepository<Scholarship, Long> {
    List<Scholarship> findAllByOrderByIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select scholarship from Scholarship scholarship where scholarship.id = :id")
    Optional<Scholarship> findByIdForUpdate(@Param("id") Long id);
}
