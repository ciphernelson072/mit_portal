CREATE TABLE IF NOT EXISTS program_outcomes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  program_id BIGINT NOT NULL,
  code VARCHAR(30) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT uk_program_outcome_code UNIQUE (program_id, code),
  CONSTRAINT fk_program_outcome_program FOREIGN KEY (program_id) REFERENCES programs(id)
);

CREATE TABLE IF NOT EXISTS accreditation_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  program_id BIGINT NOT NULL,
  accrediting_body VARCHAR(200) NOT NULL,
  certificate_reference VARCHAR(100),
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  valid_from DATE NULL,
  valid_until DATE NULL,
  evidence_path VARCHAR(500),
  reviewed_by VARCHAR(255),
  reviewed_at DATETIME NULL,
  CONSTRAINT fk_accreditation_program FOREIGN KEY (program_id) REFERENCES programs(id),
  INDEX idx_accreditation_program_status (program_id, status)
);