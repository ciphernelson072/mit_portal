CREATE TABLE IF NOT EXISTS course_sections (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id BIGINT NOT NULL,
  academic_term_id BIGINT NOT NULL,
  section_code VARCHAR(30) NOT NULL,
  capacity INT NOT NULL DEFAULT 50,
  active TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT uk_section_term_code UNIQUE (academic_term_id, section_code),
  CONSTRAINT fk_section_course FOREIGN KEY (course_id) REFERENCES courses(id),
  CONSTRAINT fk_section_term FOREIGN KEY (academic_term_id) REFERENCES academic_terms(id),
  INDEX idx_section_term (academic_term_id)
);

CREATE TABLE IF NOT EXISTS course_registrations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  section_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
  registered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  dropped_at DATETIME NULL,
  CONSTRAINT uk_registration_student_section UNIQUE (student_id, section_id),
  CONSTRAINT fk_registration_student FOREIGN KEY (student_id) REFERENCES students(id),
  CONSTRAINT fk_registration_section FOREIGN KEY (section_id) REFERENCES course_sections(id),
  INDEX idx_registration_student_status (student_id, status),
  INDEX idx_registration_section_status (section_id, status)
);