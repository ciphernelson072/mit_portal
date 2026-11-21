SET @fee_status_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'fees'
      AND column_name = 'status'
);
SET @fee_status_ddl = IF(
    @fee_status_exists = 0,
    'ALTER TABLE fees ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''ACTIVE''',
    'SELECT 1'
);
PREPARE fee_status_statement FROM @fee_status_ddl;
EXECUTE fee_status_statement;
DEALLOCATE PREPARE fee_status_statement;

UPDATE fees
SET status = 'ACTIVE'
WHERE status IS NULL;

SET @fee_status_check_exists = (
    SELECT COUNT(*)
    FROM information_schema.table_constraints
    WHERE table_schema = DATABASE()
      AND table_name = 'fees'
      AND constraint_name = 'chk_fee_status'
);
SET @fee_status_check_ddl = IF(
    @fee_status_check_exists = 0,
    'ALTER TABLE fees ADD CONSTRAINT chk_fee_status CHECK (status IN (''DRAFT'', ''ACTIVE''))',
    'SELECT 1'
);
PREPARE fee_status_check_statement FROM @fee_status_check_ddl;
EXECUTE fee_status_check_statement;
DEALLOCATE PREPARE fee_status_check_statement;
