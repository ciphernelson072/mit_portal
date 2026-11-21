CREATE TABLE IF NOT EXISTS announcement_program_targets (
    announcement_id BIGINT NOT NULL,
    program_id BIGINT NOT NULL,
    PRIMARY KEY (announcement_id, program_id),
    CONSTRAINT fk_announcement_program_announcement
        FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_program_program
        FOREIGN KEY (program_id) REFERENCES programs(id)
);

CREATE TABLE IF NOT EXISTS announcement_course_targets (
    announcement_id BIGINT NOT NULL,
    course_id BIGINT NOT NULL,
    PRIMARY KEY (announcement_id, course_id),
    CONSTRAINT fk_announcement_course_announcement
        FOREIGN KEY (announcement_id) REFERENCES announcements(id) ON DELETE CASCADE,
    CONSTRAINT fk_announcement_course_course
        FOREIGN KEY (course_id) REFERENCES courses(id)
);
