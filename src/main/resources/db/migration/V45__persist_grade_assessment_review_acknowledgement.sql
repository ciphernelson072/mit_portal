ALTER TABLE grades
    ADD COLUMN assessment_review_fingerprint CHAR(64) NULL,
    ADD COLUMN assessment_reviewed_by VARCHAR(255) NULL,
    ADD COLUMN assessment_reviewed_at DATETIME(6) NULL;
