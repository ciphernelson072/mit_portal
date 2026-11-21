CREATE TABLE IF NOT EXISTS mfa_recovery_codes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    code_hash CHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_mfa_recovery_code_hash UNIQUE (code_hash),
    CONSTRAINT fk_mfa_recovery_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_mfa_recovery_user (user_id)
);
