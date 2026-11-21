CREATE TABLE IF NOT EXISTS admission_document_requirements (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    required BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uk_admission_document_requirement_name UNIQUE (name)
);

CREATE TABLE IF NOT EXISTS admission_application_documents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    requirement_id BIGINT NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uk_admission_application_requirement UNIQUE (application_id, requirement_id),
    CONSTRAINT fk_admission_document_application FOREIGN KEY (application_id)
        REFERENCES admission_applications (id) ON DELETE CASCADE,
    CONSTRAINT fk_admission_document_requirement FOREIGN KEY (requirement_id)
        REFERENCES admission_document_requirements (id),
    INDEX idx_admission_document_application (application_id, uploaded_at)
);
