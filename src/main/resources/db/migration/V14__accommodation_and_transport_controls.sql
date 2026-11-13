CREATE TABLE IF NOT EXISTS hostel_rooms (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_code VARCHAR(40) NOT NULL UNIQUE,
  building VARCHAR(120) NOT NULL,
  capacity INT NOT NULL,
  available_beds INT NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS hostel_allocations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hostel_room_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  application_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  allocated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_hostel_allocation_room FOREIGN KEY (hostel_room_id) REFERENCES hostel_rooms(id),
  CONSTRAINT fk_hostel_allocation_student FOREIGN KEY (student_id) REFERENCES students(id),
  CONSTRAINT fk_hostel_allocation_application FOREIGN KEY (application_id) REFERENCES hostel_applications(id),
  INDEX idx_hostel_allocation_student (student_id)
);