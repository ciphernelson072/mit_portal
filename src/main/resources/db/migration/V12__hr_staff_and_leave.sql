CREATE TABLE IF NOT EXISTS staff (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  employee_number VARCHAR(40) NOT NULL UNIQUE,
  full_name VARCHAR(200) NOT NULL,
  title VARCHAR(150) NOT NULL,
  department_id BIGINT NULL,
  hire_date DATE NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT fk_staff_user FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS leave_requests (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  staff_id BIGINT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewed_by VARCHAR(255),
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_leave_staff FOREIGN KEY (staff_id) REFERENCES staff(id),
  INDEX idx_leave_staff_status (staff_id, status),
  INDEX idx_leave_status_created (status, created_at)
);