SET @notification_category_column_sql = IF(
  (SELECT COUNT(1)
   FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'notifications'
     AND column_name = 'category') = 0,
  'ALTER TABLE notifications ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT ''SYSTEM'' AFTER message',
  'SELECT 1'
);
PREPARE add_notification_category_column FROM @notification_category_column_sql;
EXECUTE add_notification_category_column;
DEALLOCATE PREPARE add_notification_category_column;
