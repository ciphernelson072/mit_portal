package com.sis.repository;

import com.sis.entity.LeaveRequest;
import com.sis.entity.LeaveRequestStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {
    @EntityGraph(attributePaths = {"staff.user"})
    List<LeaveRequest> findByStaffIdOrderByCreatedAtDesc(Long staffId);

    @EntityGraph(attributePaths = {"staff.user"})
    List<LeaveRequest> findByStatusOrderByCreatedAtAsc(LeaveRequestStatus status);

    @EntityGraph(attributePaths = {"staff.user"})
    List<LeaveRequest> findByStaffIdAndStatus(Long staffId, LeaveRequestStatus status);

    @EntityGraph(attributePaths = {"staff.user"})
    @Query("select leaveRequest from LeaveRequest leaveRequest where leaveRequest.id = :id")
    Optional<LeaveRequest> findByIdWithStaffUser(@Param("id") Long id);
}