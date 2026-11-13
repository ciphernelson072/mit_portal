package com.sis;

import com.sis.dto.CreateTimetableRequest;
import org.junit.jupiter.api.Test;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimetableRequestValidationTest {
    @Test
    void acceptsDayNamesRegardlessOfCaseAndSurroundingWhitespace() {
        CreateTimetableRequest request = request(" monday ");

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void rejectsUnrecognizedDayNames() {
        CreateTimetableRequest request = request("Funday");

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("dayOfWeek"));
        }
    }

    @Test
    void requiresAcademicTermForNewTimetableEntries() {
        CreateTimetableRequest request = request("Monday");
        request.setAcademicTermId(null);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .anySatisfy(violation -> assertThat(violation.getPropertyPath().toString())
                            .isEqualTo("academicTermId"));
        }
    }

    private CreateTimetableRequest request(String dayOfWeek) {
        CreateTimetableRequest request = new CreateTimetableRequest();
        request.setCourseId(1L);
        request.setAcademicTermId(1L);
        request.setDayOfWeek(dayOfWeek);
        request.setStartsAt(LocalTime.of(9, 0));
        request.setEndsAt(LocalTime.of(10, 0));
        request.setVenue("Room 1");
        return request;
    }
}
