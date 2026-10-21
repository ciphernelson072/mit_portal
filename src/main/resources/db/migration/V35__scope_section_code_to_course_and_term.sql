ALTER TABLE course_sections
  DROP INDEX uk_section_term_code,
  ADD CONSTRAINT uk_section_term_course_code
    UNIQUE (academic_term_id, course_id, section_code);
