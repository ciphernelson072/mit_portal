CREATE TABLE IF NOT EXISTS library_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  isbn VARCHAR(30) NOT NULL UNIQUE,
  title VARCHAR(255) NOT NULL,
  author VARCHAR(200) NOT NULL,
  total_copies INT NOT NULL,
  available_copies INT NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS library_loans (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  library_item_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  borrowed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  due_date DATE NOT NULL,
  returned_at DATETIME NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'BORROWED',
  CONSTRAINT fk_library_loan_item FOREIGN KEY (library_item_id) REFERENCES library_items(id),
  CONSTRAINT fk_library_loan_student FOREIGN KEY (student_id) REFERENCES students(id),
  INDEX idx_library_loan_student (student_id),
  INDEX idx_library_loan_status_due (status, due_date)
);