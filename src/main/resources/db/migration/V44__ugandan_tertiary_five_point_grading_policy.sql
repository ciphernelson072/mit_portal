ALTER TABLE courses
    ADD COLUMN is_core TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE programs
    ADD COLUMN award_type VARCHAR(20) NULL;

ALTER TABLE grades
    ADD COLUMN retake_required TINYINT(1) NOT NULL DEFAULT 0;

ALTER TABLE grades
    ADD COLUMN grading_policy_version VARCHAR(32) NULL;

UPDATE grades
SET grade_value = CASE
    WHEN total_mark >= 80 AND total_mark <= 100 THEN 'A'
    WHEN total_mark >= 75 AND total_mark < 80 THEN 'B+'
    WHEN total_mark >= 70 AND total_mark < 75 THEN 'B'
    WHEN total_mark >= 65 AND total_mark < 70 THEN 'C+'
    WHEN total_mark >= 60 AND total_mark < 65 THEN 'C'
    WHEN total_mark >= 55 AND total_mark < 60 THEN 'D+'
    WHEN total_mark >= 50 AND total_mark < 55 THEN 'D'
    WHEN total_mark >= 0 AND total_mark < 50 THEN 'F'
    ELSE grade_value
END
WHERE total_mark IS NOT NULL;

UPDATE grades
SET gpa_score = CASE UPPER(TRIM(grade_value))
    WHEN 'A' THEN 5.00
    WHEN 'B+' THEN 4.50
    WHEN 'B' THEN 4.00
    WHEN 'C+' THEN 3.50
    WHEN 'C' THEN 3.00
    WHEN 'D+' THEN 2.50
    WHEN 'D' THEN 2.00
    WHEN 'F' THEN 0.00
    ELSE gpa_score
END;

UPDATE grades
SET retake_required = CASE
    WHEN total_mark IS NOT NULL AND total_mark >= 0 AND total_mark < 50 THEN 1
    WHEN UPPER(TRIM(grade_value)) = 'F' THEN 1
    ELSE 0
END;

UPDATE grades
SET grading_policy_version = CASE
    WHEN UPPER(TRIM(grade_value)) IN ('A', 'B+', 'B', 'C+', 'C', 'D+', 'D', 'F')
        THEN 'UG_5_POINT_V1'
    ELSE 'LEGACY_UNVERIFIED'
END;

UPDATE grades
SET grade_value = 'PEND'
WHERE UPPER(TRIM(grade_value)) = 'PENDING';

ALTER TABLE grades
    MODIFY COLUMN grading_policy_version VARCHAR(32) NOT NULL DEFAULT 'UG_5_POINT_V1';
