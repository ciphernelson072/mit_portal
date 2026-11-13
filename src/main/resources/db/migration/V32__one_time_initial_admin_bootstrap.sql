CREATE TABLE IF NOT EXISTS deployment_bootstrap_state (
  bootstrap_key VARCHAR(64) NOT NULL PRIMARY KEY,
  completed_at TIMESTAMP NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO deployment_bootstrap_state (bootstrap_key, completed_at)
VALUES ('initial-admin', NULL);
