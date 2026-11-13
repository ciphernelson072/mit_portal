CREATE TABLE IF NOT EXISTS research_projects (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  abstract_text VARCHAR(2000),
  supervisor_user_id BIGINT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED',
  reviewed_by VARCHAR(255),
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_research_student FOREIGN KEY (student_id) REFERENCES students(id),
  CONSTRAINT fk_research_supervisor FOREIGN KEY (supervisor_user_id) REFERENCES users(id),
  INDEX idx_research_student_status (student_id, status)
);

CREATE TABLE IF NOT EXISTS thesis_submissions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  research_project_id BIGINT NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  notes VARCHAR(2000),
  status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
  reviewed_by VARCHAR(255),
  reviewed_at DATETIME NULL,
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_thesis_research_project FOREIGN KEY (research_project_id) REFERENCES research_projects(id),
  INDEX idx_thesis_status_submitted (status, submitted_at)
);