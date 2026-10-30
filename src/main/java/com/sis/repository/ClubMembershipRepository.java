package com.sis.repository;

import com.sis.entity.ClubMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ClubMembershipRepository extends JpaRepository<ClubMembership, Long> {
    List<ClubMembership> findByStudentIdAndStatus(Long studentId, String status);
    Optional<ClubMembership> findByClubIdAndStudentId(Long clubId, Long studentId);
    List<ClubMembership> findByClubIdInAndStudentId(List<Long> clubIds, Long studentId);
    long countByClubIdAndStatus(Long clubId, String status);

    @Query("select membership.clubId, count(membership.id) from ClubMembership membership "
            + "where membership.clubId in :clubIds and membership.status = 'ACTIVE' "
            + "group by membership.clubId")
    List<Object[]> countActiveByClubIds(@Param("clubIds") List<Long> clubIds);
}
