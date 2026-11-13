CREATE TABLE IF NOT EXISTS admission_applications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  application_number VARCHAR(30) NOT NULL UNIQUE,
  applicant_name VARCHAR(200) NOT NULL,
  email VARCHAR(255) NOT NULL,
  phone VARCHAR(30),
  program_id BIGINT NOT NULL,
  academic_year_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
  reviewer_notes VARCHAR(2000),
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  decided_at DATETIME NULL,
  CONSTRAINT fk_admission_program FOREIGN KEY (program_id) REFERENCES programs(id),
  CONSTRAINT fk_admission_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
  INDEX idx_admission_status (status),
  INDEX idx_admission_email_year (email, academic_year_id)
);

CREATE TABLE IF NOT EXISTS student_programs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  program_id BIGINT NOT NULL,
  academic_year_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  start_date DATE NOT NULL,
  end_date DATE NULL,
  CONSTRAINT uk_student_program_year UNIQUE (student_id, program_id, academic_year_id),
  CONSTRAINT fk_student_program_student FOREIGN KEY (student_id) REFERENCES students(id),
  CONSTRAINT fk_student_program_program FOREIGN KEY (program_id) REFERENCES programs(id),
  CONSTRAINT fk_student_program_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
  INDEX idx_student_program_student (student_id)
);

CREATE TABLE IF NOT EXISTS student_status_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  reason VARCHAR(2000),
  changed_by VARCHAR(255) NOT NULL,
  changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_student_status_student FOREIGN KEY (student_id) REFERENCES students(id),
  INDEX idx_student_status_student (student_id),
  INDEX idx_student_status_changed_at (changed_at)
);