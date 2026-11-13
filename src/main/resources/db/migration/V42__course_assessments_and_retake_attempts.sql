CREATE TABLE course_assessment_components (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_id BIGINT NOT NULL,
    component_code VARCHAR(40) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    component_type VARCHAR(20) NOT NULL DEFAULT 'OTHER',
    weight_percentage DECIMAL(5,2) NOT NULL,
    max_score DECIMAL(7,2) NOT NULL,
    required_component TINYINT(1) NOT NULL DEFAULT 1,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT pk_course_assessment_components PRIMARY KEY (id),
    CONSTRAINT uk_course_assessment_component_code UNIQUE (course_id, component_code),
    CONSTRAINT fk_course_assessment_component_course
        FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE
);

CREATE TABLE grade_assessment_marks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    grade_id BIGINT NOT NULL,
    assessment_component_id BIGINT NOT NULL,
    score DECIMAL(7,2) NULL,
    remarks VARCHAR(1024) NULL,
    CONSTRAINT pk_grade_assessment_marks PRIMARY KEY (id),
    CONSTRAINT uk_grade_assessment_mark UNIQUE (grade_id, assessment_component_id),
    CONSTRAINT fk_grade_assessment_mark_grade
        FOREIGN KEY (grade_id) REFERENCES grades (id) ON DELETE CASCADE,
    CONSTRAINT fk_grade_assessment_mark_component
        FOREIGN KEY (assessment_component_id) REFERENCES course_assessment_components (id) ON DELETE RESTRICT
);

ALTER TABLE grades
    ADD COLUMN attempt_number INT NOT NULL DEFAULT 1,
    ADD COLUMN original_attempt_id BIGINT NULL,
    ADD COLUMN total_mark DECIMAL(6,2) NULL,
    ADD COLUMN row_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE grades
    ADD CONSTRAINT fk_grade_original_attempt
        FOREIGN KEY (original_attempt_id) REFERENCES grades (id) ON DELETE RESTRICT;

ALTER TABLE grades
    ADD INDEX idx_grade_original_attempt (original_attempt_id);
