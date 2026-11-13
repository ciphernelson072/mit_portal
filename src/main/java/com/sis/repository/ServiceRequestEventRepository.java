package com.sis.repository;

import com.sis.entity.ServiceRequestEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ServiceRequestEventRepository extends JpaRepository<ServiceRequestEvent, Long> {
    @Query("select event from ServiceRequestEvent event " +
            "where event.serviceRequest.id = :requestId order by event.createdAt asc, event.id asc")
    List<ServiceRequestEvent> findForRequestInOrder(@Param("requestId") Long requestId);
}
