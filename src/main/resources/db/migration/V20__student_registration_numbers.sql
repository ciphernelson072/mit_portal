SET @registration_number_column_exists = (
  SELECT COUNT(1)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'students'
    AND column_name = 'registration_number'
);
SET @registration_number_column_sql = IF(
  @registration_number_column_exists = 0,
  'ALTER TABLE students ADD COLUMN registration_number VARCHAR(40) NULL',
  'SELECT 1'
);
PREPARE add_registration_number_column FROM @registration_number_column_sql;
EXECUTE add_registration_number_column;
DEALLOCATE PREPARE add_registration_number_column;

UPDATE students
SET registration_number = CONCAT(
  'MIT/',
  LEFT(COALESCE(NULLIF(enrollment_date, ''), YEAR(CURRENT_DATE)), 4),
  '/',
  LPAD(id, 5, '0')
)
WHERE registration_number IS NULL OR registration_number = '';

ALTER TABLE students MODIFY registration_number VARCHAR(40) NOT NULL;

SET @registration_number_constraint_exists = (
  SELECT COUNT(1)
  FROM information_schema.statistics
  WHERE table_schema = DATABASE()
    AND table_name = 'students'
    AND index_name = 'uk_student_registration_number'
);
SET @registration_number_constraint_sql = IF(
  @registration_number_constraint_exists = 0,
  'ALTER TABLE students ADD CONSTRAINT uk_student_registration_number UNIQUE (registration_number)',
  'SELECT 1'
);
PREPARE add_registration_number_constraint FROM @registration_number_constraint_sql;
EXECUTE add_registration_number_constraint;
DEALLOCATE PREPARE add_registration_number_constraint;