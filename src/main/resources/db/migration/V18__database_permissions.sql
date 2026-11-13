CREATE TABLE IF NOT EXISTS permissions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  description VARCHAR(255) NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS role_permissions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_name VARCHAR(50) NOT NULL,
  permission_id BIGINT NOT NULL,
  CONSTRAINT uk_role_permission UNIQUE (role_name, permission_id),
  CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permissions(id),
  INDEX idx_role_permission_role (role_name)
);