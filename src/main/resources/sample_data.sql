-- ============================================================================
-- Sample data for the Makerere Institute of Technology Student Information Portal (sis_db)
-- Run this AFTER schema.sql has created the tables (schema.sql already runs
-- application startup does not run this file - it is a separate, optional
-- convenience for populating realistic sample data by hand,
-- e.g. `mysql -u root sis_db < sample_data.sql`, or for seeding a fresh
-- environment without relying on SampleDataLoader's programmatic Java seeding).
--
-- Passwords are real BCrypt hashes (verified against Spring Security's
-- BCryptPasswordEncoder), not placeholders:
--   admin / admin123        (id 1)
--   teacher accounts below  / teacher123
--   student accounts below  / student123
--
-- Uses INSERT IGNORE with explicit IDs so this file is safe to re-run against
-- a database that already has this data (primary-key collisions are silently
-- skipped rather than erroring).
-- ============================================================================

USE sis_db;

-- ============================================================================
-- Users (1 admin, 3 teachers, 6 students)
-- ============================================================================
INSERT IGNORE INTO users (id, username, password, role, email, phone, is_active) VALUES
  (1,  'admin',    '$2b$10$y7f3g69zPAgU0qyqMrb6w.DHUB8n00aI6FrAPHySrfC2eek.3R19q', 'ADMIN',   'admin@mit.ac.ug',      '+256700000001', 1),
  (2,  'teacher1', '$2b$10$rrMiXulOBbJCJZ4zyPc0l.qzn4wzaGQYf9InaSQqBNGTKplLZnN5y', 'TEACHER', 'nakato.r@mit.ac.ug',   '+256700000002', 1),
  (3,  'teacher2', '$2b$10$rrMiXulOBbJCJZ4zyPc0l.qzn4wzaGQYf9InaSQqBNGTKplLZnN5y', 'TEACHER', 'okello.d@mit.ac.ug',   '+256700000003', 1),
  (4,  'teacher3', '$2b$10$rrMiXulOBbJCJZ4zyPc0l.qzn4wzaGQYf9InaSQqBNGTKplLZnN5y', 'TEACHER', 'atim.g@mit.ac.ug',     '+256700000004', 1),
  (5,  'student1', '$2b$10$R.h4wY0P9RyhG7MsTfNd9uUAr1QGxmns/u1SUE.9K.2AJwORirOfW', 'STUDENT', 'ssemakula.j@mit.ac.ug','+256700000005', 1),
  (6,  'student2', '$2b$10$R.h4wY0P9RyhG7MsTfNd9uUAr1QGxmns/u1SUE.9K.2AJwORirOfW', 'STUDENT', 'nabirye.p@mit.ac.ug',  '+256700000006', 1),
  (7,  'student3', '$2b$10$R.h4wY0P9RyhG7MsTfNd9uUAr1QGxmns/u1SUE.9K.2AJwORirOfW', 'STUDENT', 'kato.m@mit.ac.ug',     '+256700000007', 1),
  (8,  'student4', '$2b$10$R.h4wY0P9RyhG7MsTfNd9uUAr1QGxmns/u1SUE.9K.2AJwORirOfW', 'STUDENT', 'namutebi.s@mit.ac.ug', '+256700000008', 1),
  (9,  'student5', '$2b$10$R.h4wY0P9RyhG7MsTfNd9uUAr1QGxmns/u1SUE.9K.2AJwORirOfW', 'STUDENT', 'wasswa.b@mit.ac.ug',   '+256700000009', 1),
  (10, 'student6', '$2b$10$R.h4wY0P9RyhG7MsTfNd9uUAr1QGxmns/u1SUE.9K.2AJwORirOfW', 'STUDENT', 'auma.l@mit.ac.ug',     '+256700000010', 1);

-- ============================================================================
-- Teachers
-- ============================================================================
INSERT IGNORE INTO teachers (id, user_id, full_name, email, phone, qualification, years_of_experience, department) VALUES
  (1, 2, 'Ruth Nakato',   'nakato.r@mit.ac.ug', '+256700000002', 'PhD Computer Science, Makerere University',      9,  'Computer Science'),
  (2, 3, 'David Okello',  'okello.d@mit.ac.ug', '+256700000003', 'MSc Electrical Engineering, MIT Cambridge',      12, 'Electrical Engineering'),
  (3, 4, 'Grace Atim',    'atim.g@mit.ac.ug',   '+256700000004', 'MSc Applied Mathematics, University of Cape Town', 6, 'Mathematics');

-- ============================================================================
-- Students
-- ============================================================================
INSERT IGNORE INTO students (id, user_id, full_name, class_name, email, phone, cgpa, enrollment_date, address) VALUES
  (1, 5,  'Joseph Ssemakula', 'Year 2 - Computer Science',        'ssemakula.j@mit.ac.ug', '+256700000005', 3.42, '2024-08-15', 'Ntinda, Kampala'),
  (2, 6,  'Patricia Nabirye', 'Year 2 - Computer Science',        'nabirye.p@mit.ac.ug',  '+256700000006', 3.71, '2024-08-15', 'Bugolobi, Kampala'),
  (3, 7,  'Moses Kato',       'Year 1 - Electrical Engineering',  'kato.m@mit.ac.ug',     '+256700000007', 2.95, '2025-08-11', 'Mukono'),
  (4, 8,  'Sarah Namutebi',   'Year 1 - Electrical Engineering',  'namutebi.s@mit.ac.ug', '+256700000008', 3.10, '2025-08-11', 'Wakiso'),
  (5, 9,  'Brian Wasswa',     'Year 3 - Mathematics',             'wasswa.b@mit.ac.ug',   '+256700000009', 3.85, '2023-08-14', 'Nansana'),
  (6, 10, 'Lillian Auma',     'Year 3 - Mathematics',             'auma.l@mit.ac.ug',     '+256700000010', 3.58, '2023-08-14', 'Kireka, Kampala');

-- ============================================================================
-- Courses (one per teacher, plus a second CS course)
-- ============================================================================
INSERT IGNORE INTO courses (id, name, course_code, category_id, teacher_id, semester, credits) VALUES
  (1, 'Data Structures & Algorithms', 'CSC201', (SELECT id FROM course_categories WHERE code = 'SCIENCES'), 1, 'Semester 1, 2025/2026', 4),
  (2, 'Database Systems',             'CSC202', (SELECT id FROM course_categories WHERE code = 'SCIENCES'), 1, 'Semester 1, 2025/2026', 3),
  (3, 'Circuit Theory',               'EEE201', (SELECT id FROM course_categories WHERE code = 'SCIENCES'), 2, 'Semester 1, 2025/2026', 4),
  (4, 'Calculus III',                 'MTH301', (SELECT id FROM course_categories WHERE code = 'SCIENCES'), 3, 'Semester 1, 2025/2026', 3);

-- ============================================================================
-- Course enrollment
-- ============================================================================
INSERT IGNORE INTO course_students (course_id, student_id) VALUES
  (1, 1), (1, 2),
  (2, 1), (2, 2),
  (3, 3), (3, 4),
  (4, 5), (4, 6);

-- ============================================================================
-- Grades
-- ============================================================================
INSERT IGNORE INTO grades (id, student_id, course_id, grade_value, gpa_score, remarks) VALUES
  (1, 1, 1, 'B+', 3.30, 'Solid grasp of tree and graph algorithms.'),
  (2, 1, 2, 'A-', 3.70, 'Excellent query optimization project.'),
  (3, 2, 1, 'A',  4.00, 'Outstanding performance all semester.'),
  (4, 2, 2, 'A-', 3.70, 'Consistently strong lab work.'),
  (5, 3, 3, 'B',  3.00, 'Good understanding, needs more practice with AC analysis.'),
  (6, 4, 3, 'B+', 3.30, 'Improved steadily over the semester.'),
  (7, 5, 4, 'A',  4.00, 'Exceptional problem-solving in multivariable calculus.'),
  (8, 6, 4, 'B+', 3.30, 'Strong conceptual understanding.');

-- ============================================================================
-- Attendance (a couple of weeks' worth per student/course pairing)
-- ============================================================================
INSERT IGNORE INTO attendance (id, student_id, course_id, date, present, remarks) VALUES
  (1, 1, 1, '2026-08-04', 1, NULL),
  (2, 1, 1, '2026-08-11', 1, NULL),
  (3, 1, 1, '2026-08-18', 0, 'Reported sick'),
  (4, 2, 1, '2026-08-04', 1, NULL),
  (5, 2, 1, '2026-08-11', 1, NULL),
  (6, 2, 1, '2026-08-18', 1, NULL),
  (7, 3, 3, '2026-08-05', 1, NULL),
  (8, 3, 3, '2026-08-12', 0, 'Transport delay'),
  (9, 4, 3, '2026-08-05', 1, NULL),
  (10, 4, 3, '2026-08-12', 1, NULL),
  (11, 5, 4, '2026-08-06', 1, NULL),
  (12, 6, 4, '2026-08-06', 1, NULL);

-- ============================================================================
-- Fees (mix of paid and outstanding, so the student Fees view has both states)
-- ============================================================================
INSERT IGNORE INTO fees (id, student_id, description, amount, due_date, paid, payment_date) VALUES
  (1, 1, 'Tuition - Semester 1', 1800000.00, '2026-09-01', 0, NULL),
  (2, 1, 'Library fee',          50000.00,   '2026-08-20', 1, '2026-08-10'),
  (3, 2, 'Tuition - Semester 1', 1800000.00, '2026-09-01', 1, '2026-08-05'),
  (4, 3, 'Tuition - Semester 1', 1750000.00, '2026-09-01', 0, NULL),
  (5, 4, 'Tuition - Semester 1', 1750000.00, '2026-09-01', 0, NULL),
  (6, 5, 'Tuition - Semester 1', 1900000.00, '2026-09-01', 1, '2026-08-01'),
  (7, 6, 'Lab equipment fee',    120000.00,  '2026-08-25', 0, NULL);

-- ============================================================================
-- Announcements
-- ============================================================================
INSERT IGNORE INTO announcements (id, title, content, author_id) VALUES
  (1, 'Semester 1 registration now open', 'All continuing students should complete course registration by August 30th via the student portal.', 1),
  (2, 'Library extended hours during exams', 'The main library will be open until midnight during the examination period starting September 15th.', 1),
  (3, 'Database Systems: project groups posted', 'Project groups for the Database Systems final project have been posted on the course materials page.', 2);

-- ============================================================================
-- Course materials
-- ============================================================================
INSERT IGNORE INTO course_materials (id, course_id, title, description, file_url, uploaded_by) VALUES
  (1, 1, 'Week 1 - Arrays & Linked Lists slides', 'Introductory slides covering array and linked list fundamentals.', '/materials/dsa/week1-slides.pdf', 2),
  (2, 1, 'Assignment 1 - Sorting Algorithms',     'Implement and compare three sorting algorithms.',                  '/materials/dsa/assignment1.pdf',   2),
  (3, 2, 'Database Systems syllabus',             'Full course syllabus and grading breakdown.',                      '/materials/dbs/syllabus.pdf',       2),
  (4, 3, 'Circuit Theory lab manual',             'Lab manual for the semester, all 8 lab sessions.',                 '/materials/circuits/lab-manual.pdf',3);

-- ============================================================================
-- Messages
-- ============================================================================
INSERT IGNORE INTO messages (id, sender_id, receiver_id, subject, content, read_status) VALUES
  (1, 5, 2, 'Question about Assignment 1', 'Hi Ms. Nakato, could you clarify the expected input size for the sorting benchmark?', 0),
  (2, 2, 5, 'Re: Question about Assignment 1', 'Good question - test with arrays of 1,000, 10,000, and 100,000 elements.', 1),
  (3, 1, 2, 'Grade submission reminder', 'Please submit Database Systems grades by end of week.', 0);

-- ============================================================================
-- Portal domains used by the backend-only frontend renderers
-- ============================================================================
INSERT IGNORE INTO assignments (id, course_id, title, description, due_at, teacher_id) VALUES
  (1, 1, 'Sorting Algorithms', 'Implement and compare three sorting algorithms.', '2026-09-20 23:59:00', 1),
  (2, 2, 'Database Project', 'Design a normalized library management database.', '2026-09-25 23:59:00', 1),
  (3, 3, 'Circuit Lab Report', 'Submit the AC analysis laboratory report.', '2026-09-22 23:59:00', 2);

INSERT IGNORE INTO exams (id, course_id, title, starts_at, venue) VALUES
  (1, 1, 'Data Structures Final Examination', '2026-10-10 10:00:00', 'Lab A1'),
  (2, 2, 'Database Systems Final Examination', '2026-10-12 14:00:00', 'Lab B2'),
  (3, 3, 'Circuit Theory Final Examination', '2026-10-14 10:00:00', 'Engineering Hall');

INSERT IGNORE INTO scholarships (id, name, description, amount, open) VALUES
  (1, 'Academic Excellence Scholarship', 'For students maintaining a CGPA of 3.5 or higher.', 1500000.00, 1),
  (2, 'Need-Based Support Scholarship', 'Financial support for eligible continuing students.', 500000.00, 1);

INSERT IGNORE INTO campus_events (id, title, description, starts_at, venue) VALUES
  (1, 'Technology Exhibition', 'Student projects and demonstrations from all computing programmes.', '2026-09-18 09:00:00', 'Student Centre'),
  (2, 'MIT Sports Day', 'Inter-faculty sports and community activities.', '2026-09-25 08:00:00', 'Sports Grounds');

INSERT IGNORE INTO clubs (id, slug, name, description, meeting_info) VALUES
  (1, 'programming', 'Programming Club', 'Learn coding, participate in hackathons, and network with fellow developers.', 'Every Thursday at 3 PM'),
  (2, 'gaming', 'Gaming Society', 'Gaming tournaments, esports competitions, and casual gaming sessions.', 'Weekends'),
  (3, 'music', 'Music & Entertainment', 'Concerts, talent shows, and performances throughout the year.', 'Fridays at 4 PM'),
  (4, 'debate', 'Debate & Drama Club', 'Develop public speaking skills and showcase your talent on stage.', 'Tuesdays at 5 PM'),
  (5, 'environment', 'Environmental Club', 'Conservation projects, awareness campaigns, and sustainability initiatives.', 'Wednesdays at 2 PM'),
  (6, 'entrepreneurship', 'Entrepreneurship Club', 'Business ideas, startup pitches, and mentorship from successful entrepreneurs.', 'Mondays at 4 PM');

INSERT IGNORE INTO transport_routes (id, name, stops) VALUES
  (1, 'Main Campus to City Centre', 'Main Campus; Makerere Gate; City Centre'),
  (2, 'Main Campus to Hostel Complex', 'Main Campus; Northern Gate; Hostel Complex');

INSERT IGNORE INTO notifications (id, user_id, title, message, read_status, created_at) VALUES
  (1, 5, 'Assignment published', 'A new Data Structures assignment is available.', 0, '2026-09-07 08:00:00'),
  (2, 5, 'Exam schedule published', 'Your examination schedule is now available.', 0, '2026-09-07 09:00:00');

INSERT IGNORE INTO payment_transactions (id, fee_id, student_id, amount, status, created_at) VALUES
  (1, 2, 1, 50000.00, 'COMPLETED', '2026-08-10 12:00:00'),
  (2, 3, 2, 1800000.00, 'COMPLETED', '2026-08-05 12:00:00');

INSERT IGNORE INTO system_settings (setting_key, setting_value) VALUES
  ('academic-calendar', '["2026-08-01","2026-12-15"]'),
  ('fee-settings', '["1800000","400000"]');
