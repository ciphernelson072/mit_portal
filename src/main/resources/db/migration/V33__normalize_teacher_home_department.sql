ALTER TABLE teachers
    ADD COLUMN department_id BIGINT NULL;

ALTER TABLE teachers
    ADD INDEX idx_teacher_department (department_id);

ALTER TABLE teachers
    ADD CONSTRAINT fk_teacher_department
        FOREIGN KEY (department_id) REFERENCES departments(id);
