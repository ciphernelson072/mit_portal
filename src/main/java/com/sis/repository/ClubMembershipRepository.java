package com.sis.repository;

import com.sis.entity.ClubMembership;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClubMembershipRepository extends JpaRepository<ClubMembership, Long> {
    List<ClubMembership> findByStudentIdAndStatus(Long studentId, String status);
    Optional<ClubMembership> findByClubIdAndStudentId(Long clubId, Long studentId);
    long countByClubIdAndStatus(Long clubId, String status);
}
