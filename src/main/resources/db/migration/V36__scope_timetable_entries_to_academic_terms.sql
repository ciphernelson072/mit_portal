ALTER TABLE timetable_entries
  ADD COLUMN academic_term_id BIGINT NULL;

ALTER TABLE timetable_entries
  ADD INDEX idx_timetable_term_course (academic_term_id, course_id);

ALTER TABLE timetable_entries
  ADD CONSTRAINT fk_timetable_academic_term
    FOREIGN KEY (academic_term_id) REFERENCES academic_terms(id);
