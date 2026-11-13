-- Forward migration for existing sis_db installations.
-- Run once against the target database before restarting with ddl-auto=validate.
USE sis_db;

ALTER TABLE courses ADD COLUMN course_code VARCHAR(30) NULL;
CREATE TABLE IF NOT EXISTS course_categories (
	id BIGINT AUTO_INCREMENT PRIMARY KEY,
	code VARCHAR(30) NOT NULL UNIQUE,
	name VARCHAR(100) NOT NULL UNIQUE,
	active TINYINT(1) NOT NULL DEFAULT 1
);
INSERT IGNORE INTO course_categories (code, name, active) VALUES
	('SCIENCES', 'Sciences', 1), ('ARTS', 'Arts', 1), ('OTHER', 'Other', 1);

ALTER TABLE courses ADD COLUMN category_id BIGINT NULL;

UPDATE courses
SET course_code = CONCAT('COURSE-', id)
WHERE course_code IS NULL OR course_code = '';

ALTER TABLE courses MODIFY course_code VARCHAR(30) NOT NULL;
ALTER TABLE courses ADD CONSTRAINT uq_courses_course_code UNIQUE (course_code);
UPDATE courses AS c
SET category_id = (SELECT id FROM course_categories WHERE code = 'OTHER')
WHERE c.category_id IS NULL
   OR c.category_id = 0
   OR NOT EXISTS (
	   SELECT 1
	   FROM course_categories AS cc
	   WHERE cc.id = c.category_id
   );
ALTER TABLE courses MODIFY category_id BIGINT NOT NULL;
ALTER TABLE courses ADD CONSTRAINT fk_course_category FOREIGN KEY (category_id) REFERENCES course_categories(id) ON DELETE RESTRICT;
