package com.sis.repository;

import com.sis.entity.Club;
import com.sis.entity.ClubMembership;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "sis.sample-data.enabled=false"
})
class ClubMembershipRepositoryTest {

    @Autowired
    private ClubRepository clubs;

    @Autowired
    private ClubMembershipRepository memberships;

    @Test
    void clubDirectoryCountsAndStudentMembershipsAreFetchedInBatches() {
        Club firstClub = clubs.saveAndFlush(new Club("first-club", "First Club", "Description", "Weekly"));
        Club secondClub = clubs.saveAndFlush(new Club("second-club", "Second Club", "Description", "Monthly"));
        ClubMembership activeFirstMembership = memberships.saveAndFlush(new ClubMembership(firstClub.getId(), 21L));
        ClubMembership inactiveFirstMembership = new ClubMembership(firstClub.getId(), 22L);
        inactiveFirstMembership.setStatus("LEFT");
        memberships.saveAndFlush(inactiveFirstMembership);
        ClubMembership activeSecondMembership = memberships.saveAndFlush(new ClubMembership(secondClub.getId(), 21L));

        Map<Long, Long> counts = memberships.countActiveByClubIds(List.of(firstClub.getId(), secondClub.getId()))
                .stream()
                .collect(Collectors.toMap(row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()));
        List<ClubMembership> joinedMemberships =
                memberships.findByClubIdInAndStudentId(List.of(firstClub.getId(), secondClub.getId()), 21L);

        assertThat(counts).containsEntry(firstClub.getId(), 1L)
                .containsEntry(secondClub.getId(), 1L);
        assertThat(joinedMemberships).extracting(ClubMembership::getId)
                .containsExactlyInAnyOrder(activeFirstMembership.getId(), activeSecondMembership.getId());
    }
}
