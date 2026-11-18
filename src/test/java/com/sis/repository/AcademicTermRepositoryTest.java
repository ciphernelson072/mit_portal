package com.sis.repository;

import com.sis.entity.AcademicTerm;
import com.sis.entity.AcademicYear;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.transaction.TestTransaction;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = "sis.sample-data.enabled=false")
class AcademicTermRepositoryTest {
    @Autowired
    private AcademicTermRepository terms;

    @Autowired
    private AcademicYearRepository years;

    @Autowired
    private EntityManager entityManager;

    @Test
    void loadsAcademicYearForTermResponsesAfterSessionCloses() {
        AcademicYear year = new AcademicYear();
        year.setCode("FETCH-AY");
        year.setName("Fetch Test Year");
        year.setStartDate(LocalDate.of(2026, 1, 1));
        year.setEndDate(LocalDate.of(2026, 12, 31));
        year = years.saveAndFlush(year);

        AcademicTerm term = new AcademicTerm();
        term.setCode("FETCH-T1");
        term.setName("Fetch Test Term");
        term.setAcademicYear(year);
        term.setStartDate(LocalDate.of(2026, 1, 1));
        term.setEndDate(LocalDate.of(2026, 6, 30));
        terms.saveAndFlush(term);
        entityManager.clear();

        List<AcademicTerm> termsForYear = terms.findByAcademicYearIdOrderByStartDateAsc(year.getId());
        entityManager.clear();
        AcademicTerm termById = terms.findWithAcademicYearById(term.getId()).orElseThrow();
        TestTransaction.end();

        assertThat(termsForYear).singleElement()
                .satisfies(result -> assertThat(result.getAcademicYear().getCode()).isEqualTo("FETCH-AY"));
        assertThat(termById.getAcademicYear().getCode()).isEqualTo("FETCH-AY");
    }
}
