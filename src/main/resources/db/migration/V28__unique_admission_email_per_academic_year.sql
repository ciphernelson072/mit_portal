ALTER TABLE admission_applications
  ADD COLUMN email_normalized VARCHAR(255) NULL;

UPDATE admission_applications
  SET email_normalized = LOWER(TRIM(email));

ALTER TABLE admission_applications
  MODIFY email_normalized VARCHAR(255) NOT NULL;

ALTER TABLE admission_applications
  ADD CONSTRAINT uk_admission_email_normalized_year
  UNIQUE (email_normalized, academic_year_id);
