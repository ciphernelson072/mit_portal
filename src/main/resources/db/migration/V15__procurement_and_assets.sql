CREATE TABLE IF NOT EXISTS suppliers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  supplier_code VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  email VARCHAR(255),
  phone VARCHAR(40),
  active TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS procurement_requests (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  requester_id BIGINT NOT NULL,
  description VARCHAR(255) NOT NULL,
  estimated_amount DECIMAL(12,2) NOT NULL,
  supplier_id BIGINT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
  reviewed_by VARCHAR(255),
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_procurement_requester FOREIGN KEY (requester_id) REFERENCES users(id),
  CONSTRAINT fk_procurement_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
  INDEX idx_procurement_status_created (status, created_at)
);

CREATE TABLE IF NOT EXISTS assets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  asset_tag VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  description VARCHAR(1000),
  purchase_value DECIMAL(12,2),
  purchase_date DATE,
  assigned_to_user_id BIGINT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT fk_asset_assignee FOREIGN KEY (assigned_to_user_id) REFERENCES users(id)
);