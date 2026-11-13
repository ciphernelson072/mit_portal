package com.sis.dto;

import org.junit.jupiter.api.Test;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;

import static org.assertj.core.api.Assertions.assertThat;

class AdmissionApplicationRequestTest {

    @Test
    void rejectsEmailLongerThanSupportedNormalizedEmailColumn() {
        AdmissionApplicationRequest request = new AdmissionApplicationRequest();
        request.setApplicantName("Applicant Name");
        request.setEmail("a".repeat(243) + "@example.com");
        request.setProgramId(1L);
        request.setAcademicYearId(1L);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(request))
                    .anySatisfy(violation -> {
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("email");
                        assertThat(violation.getMessage()).isEqualTo("size must be between 0 and 254");
                    });
        }
    }
}
