SET @username_normalized_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'users'
    AND column_name = 'username_normalized'
);

SET @identity_ddl = IF(
  @username_normalized_exists = 0,
  'ALTER TABLE users ADD COLUMN username_normalized VARCHAR(255) NULL',
  'SELECT 1'
);
PREPARE identity_statement FROM @identity_ddl;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SET @email_normalized_exists = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND table_name = 'users'
    AND column_name = 'email_normalized'
);

SET @identity_ddl = IF(
  @email_normalized_exists = 0,
  'ALTER TABLE users ADD COLUMN email_normalized VARCHAR(255) NULL',
  'SELECT 1'
);
PREPARE identity_statement FROM @identity_ddl;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

UPDATE users SET username_normalized = LOWER(TRIM(username));

UPDATE users
SET email_normalized = LOWER(TRIM(email))
WHERE email IS NOT NULL;

ALTER TABLE users
  MODIFY username_normalized VARCHAR(255) NOT NULL,
  MODIFY email_normalized VARCHAR(255) NULL;

SET @username_unique_exists = (
  SELECT COUNT(*)
  FROM (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND non_unique = 0
    GROUP BY index_name
    HAVING COUNT(*) = 1
      AND MAX(column_name) = 'username_normalized'
  ) AS username_indexes
);

SET @identity_ddl = IF(
  @username_unique_exists = 0,
  'ALTER TABLE users ADD CONSTRAINT uk_users_username_normalized UNIQUE (username_normalized)',
  'SELECT 1'
);
PREPARE identity_statement FROM @identity_ddl;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;

SET @email_unique_exists = (
  SELECT COUNT(*)
  FROM (
    SELECT index_name
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'users'
      AND non_unique = 0
    GROUP BY index_name
    HAVING COUNT(*) = 1
      AND MAX(column_name) = 'email_normalized'
  ) AS email_indexes
);

SET @identity_ddl = IF(
  @email_unique_exists = 0,
  'ALTER TABLE users ADD CONSTRAINT uk_users_email_normalized UNIQUE (email_normalized)',
  'SELECT 1'
);
PREPARE identity_statement FROM @identity_ddl;
EXECUTE identity_statement;
DEALLOCATE PREPARE identity_statement;
