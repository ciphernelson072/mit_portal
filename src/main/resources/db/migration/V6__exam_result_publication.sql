SET @published_column_sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'exam_results'
     AND column_name = 'published') = 0,
  'ALTER TABLE exam_results ADD COLUMN published TINYINT(1) NOT NULL DEFAULT 1',
  'SELECT 1'
);
PREPARE add_published_column FROM @published_column_sql;
EXECUTE add_published_column;
DEALLOCATE PREPARE add_published_column;

SET @published_at_column_sql = IF(
  (SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'exam_results'
     AND column_name = 'published_at') = 0,
  'ALTER TABLE exam_results ADD COLUMN published_at DATETIME NULL',
  'SELECT 1'
);
PREPARE add_published_at_column FROM @published_at_column_sql;
EXECUTE add_published_at_column;
DEALLOCATE PREPARE add_published_at_column;