ALTER TABLE student_status_history
    ADD COLUMN student_program_id BIGINT NULL;

ALTER TABLE student_status_history
    ADD INDEX idx_student_status_program (student_program_id);

ALTER TABLE student_status_history
    ADD CONSTRAINT fk_student_status_program
        FOREIGN KEY (student_program_id) REFERENCES student_programs(id);
