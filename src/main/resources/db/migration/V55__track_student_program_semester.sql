SET @student_program_semester_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'student_programs'
      AND column_name = 'current_semester'
);
SET @student_program_semester_ddl = IF(
    @student_program_semester_exists = 0,
    'ALTER TABLE student_programs ADD COLUMN current_semester INT NOT NULL DEFAULT 1',
    'SELECT 1'
);
PREPARE student_program_semester_statement FROM @student_program_semester_ddl;
EXECUTE student_program_semester_statement;
DEALLOCATE PREPARE student_program_semester_statement;
