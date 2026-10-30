package com.sis.repository;

import com.sis.entity.Assignment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "sis.sample-data.enabled=false"
})
class AssignmentRepositoryTest {

    @Autowired
    private AssignmentRepository assignments;

    @Test
    void courseFeedReturnsEveryAssignmentOrderedByDueDate() {
        LocalDateTime now = LocalDateTime.now();
        Assignment laterDue = assignment(41L, "Later", now.plusDays(2));
        Assignment earlierDue = assignment(41L, "Earlier", now.plusDays(1));
        Assignment unrelated = assignment(42L, "Other course", now);
        assignments.saveAllAndFlush(List.of(laterDue, earlierDue, unrelated));

        assertThat(assignments.findByCourseIdInOrderByDueAtAsc(List.of(41L)))
                .extracting(Assignment::getTitle)
                .containsExactly("Earlier", "Later");
    }

    private Assignment assignment(Long courseId, String title, LocalDateTime dueAt) {
        Assignment assignment = new Assignment();
        assignment.setCourseId(courseId);
        assignment.setTitle(title);
        assignment.setDueAt(dueAt);
        assignment.setTeacherId(9L);
        return assignment;
    }
}
