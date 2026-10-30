package com.sis.dto;

import org.junit.jupiter.api.Test;

import javax.validation.Validation;
import javax.validation.ValidatorFactory;

import static org.assertj.core.api.Assertions.assertThat;

class RequestValidationTest {

    @Test
    void rejectsInvalidOperationalRequestBodies() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();

            CreateExamPermitRequest permit = new CreateExamPermitRequest();
            assertThat(validator.validate(permit)).extracting(v -> v.getPropertyPath().toString())
                    .contains("examId");
            permit.setExamId(0L);
            assertThat(validator.validate(permit)).extracting(v -> v.getPropertyPath().toString())
                    .contains("examId");

            RejectionRequest rejection = new RejectionRequest();
            rejection.setReason("x".repeat(1001));
            assertThat(validator.validate(rejection)).extracting(v -> v.getPropertyPath().toString())
                    .contains("reason");

            HostelApplicationRequest hostel = new HostelApplicationRequest();
            hostel.setDetails("x".repeat(2001));
            assertThat(validator.validate(hostel)).extracting(v -> v.getPropertyPath().toString())
                    .contains("details");

            SendCampusChatRequest chat = new SendCampusChatRequest();
            chat.setContent("   ");
            assertThat(validator.validate(chat)).extracting(v -> v.getPropertyPath().toString())
                    .contains("content");
            chat.setContent("x".repeat(5001));
            assertThat(validator.validate(chat)).extracting(v -> v.getPropertyPath().toString())
                    .contains("content");

            AssignmentSubmissionRequest submission = new AssignmentSubmissionRequest();
            submission.setContent("x".repeat(2001));
            assertThat(validator.validate(submission)).extracting(v -> v.getPropertyPath().toString())
                    .contains("content");
            submission.setContent("  ");
            assertThat(validator.validate(submission)).extracting(v -> v.getPropertyPath().toString())
                    .contains("content");

            SaveSystemSettingRequest setting = new SaveSystemSettingRequest();
            setting.setValue("x".repeat(4001));
            assertThat(validator.validate(setting)).extracting(v -> v.getPropertyPath().toString())
                    .contains("value");

        }
    }

    @Test
    void keepsOptionalRequestFieldsWithinTheirSupportedLimits() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(new RejectionRequest())).isEmpty();
            assertThat(factory.getValidator().validate(new HostelApplicationRequest())).isEmpty();
            AssignmentSubmissionRequest submission = new AssignmentSubmissionRequest();
            submission.setContent("Submitted work");
            assertThat(factory.getValidator().validate(submission)).isEmpty();
            assertThat(factory.getValidator().validate(new SaveSystemSettingRequest())).isEmpty();
        }
    }
}
