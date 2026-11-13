CREATE TABLE IF NOT EXISTS curriculum_courses (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  program_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  semester_number INT NOT NULL,
  required TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT uk_curriculum_program_course UNIQUE (program_id, course_id),
  CONSTRAINT fk_curriculum_program FOREIGN KEY (program_id) REFERENCES programs(id),
  CONSTRAINT fk_curriculum_course FOREIGN KEY (course_id) REFERENCES courses(id)
);

CREATE TABLE IF NOT EXISTS course_prerequisites (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id BIGINT NOT NULL,
  prerequisite_course_id BIGINT NOT NULL,
  CONSTRAINT uk_prerequisite_course UNIQUE (course_id, prerequisite_course_id),
  CONSTRAINT fk_prerequisite_course FOREIGN KEY (course_id) REFERENCES courses(id),
  CONSTRAINT fk_prerequisite_required FOREIGN KEY (prerequisite_course_id) REFERENCES courses(id)
);