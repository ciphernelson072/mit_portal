package com.sis.service;

public class RegistrationRuleViolation extends IllegalStateException {
    private final Rule rule;

    public RegistrationRuleViolation(Rule rule, String message) {
        super(message);
        this.rule = rule;
    }

    public Rule getRule() {
        return rule;
    }

    public enum Rule {
        REGISTRATION_CLOSED,
        NO_ACTIVE_PROGRAM,
        COURSE_NOT_IN_CURRICULUM,
        FINANCIAL_HOLD,
        ALREADY_REGISTERED,
        TIMETABLE_CONFLICT,
        PREREQUISITE_NOT_COMPLETED,
        SECTION_FULL,
        INSTRUCTOR_UNAVAILABLE
    }
}
