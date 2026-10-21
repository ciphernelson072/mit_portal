CREATE TABLE IF NOT EXISTS course_units (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  unit_code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  course_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  CONSTRAINT fk_course_unit_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_course_unit_teacher FOREIGN KEY (teacher_id) REFERENCES teachers(id) ON DELETE RESTRICT,
  INDEX idx_course_unit_course (course_id),
  INDEX idx_course_unit_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS course_unit_students (
  course_unit_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  PRIMARY KEY (course_unit_id, student_id),
  CONSTRAINT fk_course_unit_student_unit FOREIGN KEY (course_unit_id) REFERENCES course_units(id) ON DELETE CASCADE,
  CONSTRAINT fk_course_unit_student_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  INDEX idx_course_unit_student_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;