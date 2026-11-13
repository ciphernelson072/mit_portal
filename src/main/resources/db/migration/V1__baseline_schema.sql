-- ============================================================================
-- Makerere Institute of Technology Student Information Portal - Enhanced Database Schema
-- Supports soft delete, CGPA calculations, profile images, and comprehensive data
-- ============================================================================

-- Users table: Core user accounts with soft-delete support
-- is_active field allows soft deletion without losing data
CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(255) NOT NULL UNIQUE COMMENT 'Unique username for login',
  username_normalized VARCHAR(255) NOT NULL UNIQUE COMMENT 'Lower-cased username used for case-insensitive uniqueness enforcement',
  password VARCHAR(255) NOT NULL COMMENT 'BCrypt encrypted password',
  role VARCHAR(50) NOT NULL COMMENT 'User role: ADMIN, TEACHER, STUDENT',
  email VARCHAR(255) UNIQUE COMMENT 'Email address for communication',
  email_normalized VARCHAR(255) UNIQUE COMMENT 'Lower-cased email used for case-insensitive uniqueness enforcement',
  phone VARCHAR(20) COMMENT 'Phone number for contact',
  is_active TINYINT(1) DEFAULT 1 COMMENT 'Soft delete flag: 1=active, 0=deleted',
  mfa_enabled TINYINT(1) NOT NULL DEFAULT 0,
  mfa_secret VARCHAR(255) NULL COMMENT 'Base32 TOTP secret for MFA enrollment',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_username (username),
  INDEX idx_username_normalized (username_normalized),
  INDEX idx_email_normalized (email_normalized),
  INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Teachers table: Information about faculty members
-- Includes qualifications, experience, and department information
CREATE TABLE IF NOT EXISTS teachers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE COMMENT 'Foreign key to users table',
  full_name VARCHAR(255) NOT NULL COMMENT 'Full name of teacher',
  profile_image_path VARCHAR(500) COMMENT 'Path to profile image in file system',
  email VARCHAR(255) COMMENT 'Teacher email address',
  phone VARCHAR(20) COMMENT 'Teacher phone number',
  qualification VARCHAR(500) COMMENT 'Academic qualifications and credentials',
  years_of_experience INT COMMENT 'Years of teaching experience',
  department VARCHAR(255) COMMENT 'Department or subject specialization',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_teacher_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_department (department)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Students table: Information about enrolled students
-- Includes CGPA tracking, enrollment dates, and personal details
CREATE TABLE IF NOT EXISTS students (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE COMMENT 'Foreign key to users table',
  full_name VARCHAR(255) NOT NULL COMMENT 'Full name of student',
  class_name VARCHAR(255) COMMENT 'Current class/grade level',
  profile_image_path VARCHAR(500) COMMENT 'Path to profile image in file system',
  email VARCHAR(255) COMMENT 'Student email address',
  phone VARCHAR(20) COMMENT 'Student phone number',
  date_of_birth VARCHAR(20),
  gender VARCHAR(30),
  alternative_phone VARCHAR(20),
  city VARCHAR(100),
  country VARCHAR(100),
  cgpa DECIMAL(4,2) DEFAULT 0.00 COMMENT 'Cumulative GPA (0.00 to 4.00)',
  enrollment_date VARCHAR(255) COMMENT 'Date of enrollment/admission',
  address VARCHAR(500) COMMENT 'Residential address of student',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_student_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_class_name (class_name),
  INDEX idx_cgpa (cgpa)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Courses table: Course/subject information
-- Includes credits for GPA calculations and semester information
CREATE TABLE IF NOT EXISTS course_categories (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL UNIQUE,
  active TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT IGNORE INTO course_categories (code, name, active) VALUES
  ('SCIENCES', 'Sciences', 1),
  ('ARTS', 'Arts', 1),
  ('OTHER', 'Other', 1);

CREATE TABLE IF NOT EXISTS courses (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL COMMENT 'Course name',
  course_code VARCHAR(30) NOT NULL UNIQUE COMMENT 'Institutional course code',
  category_id BIGINT NOT NULL COMMENT 'Managed course category',
  teacher_id BIGINT NOT NULL COMMENT 'Foreign key to teachers table',
  semester VARCHAR(50) COMMENT 'Academic semester (e.g., Fall 2024, Spring 2024)',
  credits INT DEFAULT 3 COMMENT 'Credit hours for GPA calculations',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES teachers(id) ON DELETE RESTRICT,
  CONSTRAINT fk_course_category FOREIGN KEY (category_id) REFERENCES course_categories(id) ON DELETE RESTRICT,
  -- idx_teacher: added for AdminService/SchoolService's teacher-scoped course
  -- lookups (findByTeacherId / findByTeacherIdWithTeacher), which previously had
  -- no index to use on this column at all.
  INDEX idx_teacher (teacher_id),
  INDEX idx_semester (semester)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS course_students (
  course_id BIGINT NOT NULL COMMENT 'Foreign key to courses table',
  student_id BIGINT NOT NULL COMMENT 'Foreign key to students table',
  PRIMARY KEY (course_id, student_id),
  CONSTRAINT fk_cs_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_cs_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  -- The composite PK (course_id, student_id) only serves lookups that start with
  -- course_id. student.getCourses() queries the other direction (by student_id
  -- alone), which needs its own index to avoid a full scan of this table.
  INDEX idx_cs_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Attendance table: Track student attendance in courses
-- Records daily attendance for audit and accountability
CREATE TABLE IF NOT EXISTS attendance (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL COMMENT 'Foreign key to students table',
  course_id BIGINT NOT NULL COMMENT 'Foreign key to courses table',
  date DATE NOT NULL COMMENT 'Date of attendance record',
  present TINYINT(1) NOT NULL COMMENT '1=present, 0=absent',
  class_start_time TIME NULL,
  class_end_time TIME NULL,
  late TINYINT(1) NOT NULL DEFAULT 0,
  remarks VARCHAR(255) COMMENT 'Additional notes or reasons',
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  CONSTRAINT fk_attendance_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT uk_attendance_student_course_date UNIQUE (student_id, course_id, date),
  INDEX idx_student_date (student_id, date),
  INDEX idx_course_date (course_id, date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Grades table: Academic grades and performance records
-- Includes both letter grades and numeric GPA scores for CGPA calculations
CREATE TABLE IF NOT EXISTS grades (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL COMMENT 'Foreign key to students table',
  course_id BIGINT NOT NULL COMMENT 'Foreign key to courses table',
  grade_value VARCHAR(5) NOT NULL COMMENT 'Letter grade (A, B, C, D, F, etc.)',
  gpa_score DECIMAL(3,2) DEFAULT 0.00 COMMENT 'Numeric GPA equivalent (0.00-4.00)',
  remarks VARCHAR(1024) COMMENT 'Additional feedback or remarks on performance',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT 'Submission workflow state: PENDING, APPROVED, REJECTED',
  submitted_by VARCHAR(255) NULL COMMENT 'Teacher username who submitted the grade',
  approved_by VARCHAR(255) NULL COMMENT 'Admin username who approved the grade',
  rejected_by VARCHAR(255) NULL COMMENT 'Admin username who rejected the grade',
  rejection_reason VARCHAR(1024) NULL COMMENT 'Reason a grade submission was rejected',
  submitted_at TIMESTAMP NULL,
  approved_at TIMESTAMP NULL,
  rejected_at TIMESTAMP NULL,
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_grade_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  CONSTRAINT fk_grade_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  INDEX idx_student (student_id),
  INDEX idx_course (course_id),
  INDEX idx_grade_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Announcements table: School announcements and notices
-- Available to all users
CREATE TABLE IF NOT EXISTS announcements (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL COMMENT 'Announcement title',
  content TEXT NOT NULL COMMENT 'Detailed announcement content',
  author_id BIGINT NOT NULL COMMENT 'Foreign key to users table (author)',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_announcement_author FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Fees table: Student fee records and payment tracking
-- Tracks financial obligations and payment status
CREATE TABLE IF NOT EXISTS fees (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL COMMENT 'Foreign key to students table',
  description VARCHAR(255) NOT NULL COMMENT 'Fee description (tuition, registration, etc.)',
  amount DECIMAL(10,2) NOT NULL COMMENT 'Fee amount',
  due_date DATE NOT NULL COMMENT 'Payment due date',
  paid TINYINT(1) DEFAULT 0 COMMENT '1=paid, 0=unpaid',
  payment_date DATE COMMENT 'Date payment was made',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_fee_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  INDEX idx_student (student_id),
  INDEX idx_paid (paid),
  INDEX idx_due_date (due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Course Materials table: Educational resources and course content
-- Tracks uploads and distribution of course materials
CREATE TABLE IF NOT EXISTS course_materials (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id BIGINT NOT NULL COMMENT 'Foreign key to courses table',
  title VARCHAR(255) NOT NULL COMMENT 'Material title',
  description TEXT COMMENT 'Detailed description of material',
  file_url VARCHAR(1024) NOT NULL COMMENT 'URL or file path to material',
  uploaded_by BIGINT NOT NULL COMMENT 'Foreign key to users table (uploader)',
  uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_material_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_material_uploader FOREIGN KEY (uploaded_by) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Messages table: Inter-user messaging system
-- Supports communication between students, teachers, and admin
CREATE TABLE IF NOT EXISTS messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  sender_id BIGINT NOT NULL COMMENT 'Foreign key to users table (sender)',
  receiver_id BIGINT NOT NULL COMMENT 'Foreign key to users table (receiver)',
  subject VARCHAR(255) NOT NULL COMMENT 'Message subject',
  content TEXT NOT NULL COMMENT 'Message content',
  sent_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  read_status TINYINT(1) DEFAULT 0 COMMENT '1=read, 0=unread',
  read_at TIMESTAMP NULL COMMENT 'Timestamp when message was read',
  CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_message_receiver FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
  -- idx_sender: getSentMessages() queries by sender_id directly - only the
  -- receiver side had an index before this.
  INDEX idx_sender (sender_id),
  INDEX idx_receiver (receiver_id),
  INDEX idx_read_status (read_status),
  INDEX idx_sent_at (sent_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Student service requests: support, events, clubs, hostel, and transport
-- requests share one auditable workflow with an explicit type and status.
CREATE TABLE IF NOT EXISTS service_requests (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NULL,
  requester_id BIGINT NULL,
  type VARCHAR(40) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  details VARCHAR(2000),
  status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_service_request_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  CONSTRAINT fk_service_request_requester FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_service_request_student (student_id),
  INDEX idx_service_request_requester (requester_id),
  INDEX idx_service_request_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Student-uploaded documents. File paths are generated by the application and
-- are only returned through student-owned download endpoints.
CREATE TABLE IF NOT EXISTS student_documents (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  name VARCHAR(100) NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  content_type VARCHAR(100) NOT NULL,
  uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
  CONSTRAINT fk_student_document_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  INDEX idx_student_document_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Portal domains: assignments, examinations, scholarships, preferences,
-- notifications, audit records, and administrator settings.
CREATE TABLE IF NOT EXISTS assignments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  description VARCHAR(2000),
  due_at DATETIME NOT NULL,
  teacher_id BIGINT NOT NULL,
  INDEX idx_assignment_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS assignment_submissions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  assignment_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  content VARCHAR(2000),
  submitted_at DATETIME NOT NULL,
  UNIQUE KEY uq_assignment_student (assignment_id, student_id),
  CONSTRAINT fk_submission_assignment FOREIGN KEY (assignment_id) REFERENCES assignments(id) ON DELETE CASCADE,
  CONSTRAINT fk_submission_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exams (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  starts_at DATETIME NOT NULL,
  venue VARCHAR(255) NOT NULL,
  INDEX idx_exam_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS exam_results (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  exam_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  score DECIMAL(6,2) NOT NULL,
  max_score DECIMAL(6,2) NOT NULL DEFAULT 100,
  remarks VARCHAR(1000),
  published TINYINT(1) NOT NULL DEFAULT 1,
  published_at DATETIME NULL,
  UNIQUE KEY uq_exam_result_student (exam_id, student_id),
  CONSTRAINT fk_exam_result_exam FOREIGN KEY (exam_id) REFERENCES exams(id) ON DELETE CASCADE,
  CONSTRAINT fk_exam_result_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS timetable_entries (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id BIGINT NOT NULL,
  day_of_week VARCHAR(20) NOT NULL,
  starts_at TIME NOT NULL,
  ends_at TIME NOT NULL,
  venue VARCHAR(255) NOT NULL,
  CONSTRAINT fk_timetable_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  INDEX idx_timetable_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scholarships (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  description VARCHAR(2000),
  amount DECIMAL(10,2) NOT NULL,
  open TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scholarship_applications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  scholarship_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
  applied_at DATETIME NOT NULL,
  UNIQUE KEY uq_scholarship_student (scholarship_id, student_id),
  CONSTRAINT fk_application_scholarship FOREIGN KEY (scholarship_id) REFERENCES scholarships(id) ON DELETE CASCADE,
  CONSTRAINT fk_application_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS user_preferences (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  language VARCHAR(20) DEFAULT 'English',
  theme VARCHAR(20) DEFAULT 'light',
  settings_json VARCHAR(4000) DEFAULT '{}',
  CONSTRAINT fk_preferences_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS notifications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  message VARCHAR(2000) NOT NULL,
  read_status TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_notification_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS system_settings (
  setting_key VARCHAR(100) PRIMARY KEY,
  setting_value VARCHAR(4000) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS audit_logs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT,
  action VARCHAR(255) NOT NULL,
  details VARCHAR(2000),
  created_at DATETIME NOT NULL,
  INDEX idx_audit_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_transactions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  fee_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'COMPLETED',
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_transaction_fee FOREIGN KEY (fee_id) REFERENCES fees(id) ON DELETE CASCADE,
  CONSTRAINT fk_transaction_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  INDEX idx_transaction_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS campus_events (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  description VARCHAR(2000),
  starts_at DATETIME NOT NULL,
  venue VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS clubs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  slug VARCHAR(100) NOT NULL UNIQUE,
  name VARCHAR(255) NOT NULL,
  description VARCHAR(2000),
  meeting_info VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS club_memberships (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  club_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  joined_at DATETIME NOT NULL,
  UNIQUE KEY uq_club_membership_student (club_id, student_id),
  CONSTRAINT fk_club_membership_club FOREIGN KEY (club_id) REFERENCES clubs(id) ON DELETE CASCADE,
  CONSTRAINT fk_club_membership_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS event_registrations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  event_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'REGISTERED',
  registered_at DATETIME NOT NULL,
  UNIQUE KEY uq_event_registration_student (event_id, student_id),
  CONSTRAINT fk_event_registration_event FOREIGN KEY (event_id) REFERENCES campus_events(id) ON DELETE CASCADE,
  CONSTRAINT fk_event_registration_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS transport_routes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  stops VARCHAR(2000) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hostel_applications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  details VARCHAR(2000),
  status VARCHAR(30) NOT NULL DEFAULT 'OPEN',
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_hostel_application_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  INDEX idx_hostel_application_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS transport_passes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  pass_type VARCHAR(50) NOT NULL DEFAULT 'STUDENT_SEMESTER',
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  valid_until DATE,
  CONSTRAINT fk_transport_pass_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  INDEX idx_transport_pass_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chat_messages (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  content VARCHAR(5000) NOT NULL,
  sender VARCHAR(30) NOT NULL,
  created_at DATETIME NOT NULL,
  CONSTRAINT fk_chat_message_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  INDEX idx_chat_message_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================================
-- Security and Authentication Tables
-- ============================================================================

-- Password Reset Tokens table: Temporary tokens for password reset functionality
-- Securely manages password reset requests with expiration
CREATE TABLE IF NOT EXISTS password_reset_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  token VARCHAR(255) NOT NULL UNIQUE COMMENT 'Unique reset token (UUID)',
  user_id BIGINT NOT NULL COMMENT 'Foreign key to users table',
  expiry_date TIMESTAMP NOT NULL COMMENT 'Token expiration time (1 hour)',
  used TINYINT(1) DEFAULT 0 COMMENT '1=used, 0=unused - prevents token reuse',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_reset_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_user (user_id),
  INDEX idx_token (token),
  INDEX idx_expiry_date (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Email Verification Tokens table: Temporary tokens for email verification
-- Manages email address verification requests with expiration
CREATE TABLE IF NOT EXISTS email_verification_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  token VARCHAR(255) NOT NULL UNIQUE COMMENT 'Unique verification token (UUID)',
  user_id BIGINT NOT NULL COMMENT 'Foreign key to users table',
  email VARCHAR(255) NOT NULL COMMENT 'Email address to be verified',
  expiry_date TIMESTAMP NOT NULL COMMENT 'Token expiration time (24 hours)',
  verified TINYINT(1) DEFAULT 0 COMMENT '1=verified, 0=unverified',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_email_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_user (user_id),
  INDEX idx_token (token),
  INDEX idx_email (email),
  INDEX idx_expiry_date (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Refresh Tokens table: server-side-revocable long-lived tokens backing the
-- HttpOnly-cookie auth flow. The access token (short-lived, in an HttpOnly cookie)
-- can't be revoked once issued - the refresh token can, by deleting/marking this row,
-- which is what makes logout, password changes, and account deactivation actually
-- take effect immediately instead of waiting out an access token's lifetime.
-- The token itself is stored hashed (SHA-256), never in plaintext, matching how
-- passwords are never stored in plaintext either.
CREATE TABLE IF NOT EXISTS refresh_tokens (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  token_hash VARCHAR(255) NOT NULL UNIQUE COMMENT 'SHA-256 hash of the refresh token',
  user_id BIGINT NOT NULL COMMENT 'Foreign key to users table',
  expiry_date TIMESTAMP NOT NULL COMMENT 'Refresh token expiration time (7 days)',
  revoked TINYINT(1) DEFAULT 0 COMMENT '1=revoked (logout/password change/deactivation), 0=active',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  INDEX idx_user (user_id),
  INDEX idx_token_hash (token_hash),
  INDEX idx_expiry_date (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Institutional hierarchy and academic periods. These tables establish the
-- university context used by admissions, registration, finance, and reporting.
CREATE TABLE IF NOT EXISTS campuses (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  address VARCHAR(500),
  active TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS faculties (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  campus_id BIGINT NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT fk_faculty_campus FOREIGN KEY (campus_id) REFERENCES campuses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS departments (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(150) NOT NULL,
  faculty_id BIGINT NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT fk_department_faculty FOREIGN KEY (faculty_id) REFERENCES faculties(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS programs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  award_title VARCHAR(150) NOT NULL,
  department_id BIGINT NOT NULL,
  duration_years INT NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT fk_program_department FOREIGN KEY (department_id) REFERENCES departments(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS academic_years (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(20) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS academic_terms (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(30) NOT NULL UNIQUE,
  name VARCHAR(100) NOT NULL,
  academic_year_id BIGINT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  registration_open TINYINT(1) NOT NULL DEFAULT 0,
  CONSTRAINT fk_term_academic_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Admissions and student lifecycle records. Admission applications are kept
-- separate from user accounts until an offer is accepted and enrollment occurs.
CREATE TABLE IF NOT EXISTS admission_applications (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  application_number VARCHAR(30) NOT NULL UNIQUE,
  applicant_name VARCHAR(200) NOT NULL,
  email VARCHAR(255) NOT NULL,
  phone VARCHAR(30),
  program_id BIGINT NOT NULL,
  academic_year_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
  reviewer_notes VARCHAR(2000),
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  decided_at DATETIME NULL,
  CONSTRAINT fk_admission_program FOREIGN KEY (program_id) REFERENCES programs(id),
  CONSTRAINT fk_admission_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
  INDEX idx_admission_status (status),
  INDEX idx_admission_email_year (email, academic_year_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student_programs (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  program_id BIGINT NOT NULL,
  academic_year_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
  start_date DATE NOT NULL,
  end_date DATE NULL,
  CONSTRAINT uk_student_program_year UNIQUE (student_id, program_id, academic_year_id),
  CONSTRAINT fk_student_program_student FOREIGN KEY (student_id) REFERENCES students(id),
  CONSTRAINT fk_student_program_program FOREIGN KEY (program_id) REFERENCES programs(id),
  CONSTRAINT fk_student_program_year FOREIGN KEY (academic_year_id) REFERENCES academic_years(id),
  INDEX idx_student_program_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS student_status_history (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  status VARCHAR(30) NOT NULL,
  reason VARCHAR(2000),
  changed_by VARCHAR(255) NOT NULL,
  changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_student_status_student FOREIGN KEY (student_id) REFERENCES students(id),
  INDEX idx_student_status_student (student_id),
  INDEX idx_student_status_changed_at (changed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Term-based course sections and student registration records.
CREATE TABLE IF NOT EXISTS course_sections (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id BIGINT NOT NULL,
  academic_term_id BIGINT NOT NULL,
  section_code VARCHAR(30) NOT NULL,
  capacity INT NOT NULL DEFAULT 50,
  active TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT uk_section_term_code UNIQUE (academic_term_id, section_code),
  CONSTRAINT fk_section_course FOREIGN KEY (course_id) REFERENCES courses(id),
  CONSTRAINT fk_section_term FOREIGN KEY (academic_term_id) REFERENCES academic_terms(id),
  INDEX idx_section_term (academic_term_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS course_registrations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  section_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'REGISTERED',
  registered_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  dropped_at DATETIME NULL,
  CONSTRAINT uk_registration_student_section UNIQUE (student_id, section_id),
  CONSTRAINT fk_registration_student FOREIGN KEY (student_id) REFERENCES students(id),
  CONSTRAINT fk_registration_section FOREIGN KEY (section_id) REFERENCES course_sections(id),
  INDEX idx_registration_student_status (student_id, status),
  INDEX idx_registration_section_status (section_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS curriculum_courses (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  program_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  semester_number INT NOT NULL,
  required TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT uk_curriculum_program_course UNIQUE (program_id, course_id),
  CONSTRAINT fk_curriculum_program FOREIGN KEY (program_id) REFERENCES programs(id),
  CONSTRAINT fk_curriculum_course FOREIGN KEY (course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS course_prerequisites (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  course_id BIGINT NOT NULL,
  prerequisite_course_id BIGINT NOT NULL,
  CONSTRAINT uk_prerequisite_course UNIQUE (course_id, prerequisite_course_id),
  CONSTRAINT fk_prerequisite_course FOREIGN KEY (course_id) REFERENCES courses(id),
  CONSTRAINT fk_prerequisite_required FOREIGN KEY (prerequisite_course_id) REFERENCES courses(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS financial_holds (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  created_by VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  active TINYINT(1) NOT NULL DEFAULT 1,
  released_at DATETIME NULL,
  CONSTRAINT fk_financial_hold_student FOREIGN KEY (student_id) REFERENCES students(id),
  INDEX idx_financial_hold_student_active (student_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_attempts (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  fee_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  provider_reference VARCHAR(120) NOT NULL UNIQUE,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  confirmed_at DATETIME NULL,
  CONSTRAINT fk_payment_attempt_fee FOREIGN KEY (fee_id) REFERENCES fees(id),
  CONSTRAINT fk_payment_attempt_student FOREIGN KEY (student_id) REFERENCES students(id),
  INDEX idx_payment_attempt_student (student_id),
  INDEX idx_payment_attempt_fee_status (fee_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_allocations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_attempt_id BIGINT NOT NULL UNIQUE,
  fee_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  allocated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_allocation_attempt FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempts(id),
  CONSTRAINT fk_allocation_fee FOREIGN KEY (fee_id) REFERENCES fees(id),
  INDEX idx_allocation_fee (fee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS fee_waivers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  fee_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  approved_by VARCHAR(255) NOT NULL,
  approved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  active TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT fk_waiver_fee FOREIGN KEY (fee_id) REFERENCES fees(id),
  INDEX idx_waiver_fee_active (fee_id, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS payment_refunds (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  payment_attempt_id BIGINT NOT NULL,
  fee_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  processed_by VARCHAR(255) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_refund_attempt FOREIGN KEY (payment_attempt_id) REFERENCES payment_attempts(id),
  CONSTRAINT fk_refund_fee FOREIGN KEY (fee_id) REFERENCES fees(id),
  CONSTRAINT fk_refund_student FOREIGN KEY (student_id) REFERENCES students(id),
  INDEX idx_refund_attempt_status (payment_attempt_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS bank_reconciliation_entries (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  provider_reference VARCHAR(120) NOT NULL UNIQUE,
  statement_amount DECIMAL(10,2) NOT NULL,
  statement_date DATE NOT NULL,
  matched_payment_attempt_id BIGINT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'UNMATCHED',
  reconciled_by VARCHAR(255),
  reconciled_at DATETIME NULL,
  CONSTRAINT fk_reconciliation_attempt FOREIGN KEY (matched_payment_attempt_id) REFERENCES payment_attempts(id),
  INDEX idx_reconciliation_status_date (status, statement_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS staff (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL UNIQUE,
  employee_number VARCHAR(40) NOT NULL UNIQUE,
  full_name VARCHAR(200) NOT NULL,
  title VARCHAR(150) NOT NULL,
  department_id BIGINT NULL,
  hire_date DATE NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT fk_staff_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS leave_requests (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  staff_id BIGINT NOT NULL,
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  reason VARCHAR(1000) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  reviewed_by VARCHAR(255),
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_leave_staff FOREIGN KEY (staff_id) REFERENCES staff(id),
  INDEX idx_leave_staff_status (staff_id, status),
  INDEX idx_leave_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS library_items (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  isbn VARCHAR(30) NOT NULL UNIQUE,
  title VARCHAR(255) NOT NULL,
  author VARCHAR(200) NOT NULL,
  total_copies INT NOT NULL,
  available_copies INT NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS library_loans (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  library_item_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  borrowed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  due_date DATE NOT NULL,
  returned_at DATETIME NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'BORROWED',
  CONSTRAINT fk_library_loan_item FOREIGN KEY (library_item_id) REFERENCES library_items(id),
  CONSTRAINT fk_library_loan_student FOREIGN KEY (student_id) REFERENCES students(id),
  INDEX idx_library_loan_student (student_id),
  INDEX idx_library_loan_status_due (status, due_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hostel_rooms (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  room_code VARCHAR(40) NOT NULL UNIQUE,
  building VARCHAR(120) NOT NULL,
  capacity INT NOT NULL,
  available_beds INT NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS hostel_allocations (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  hostel_room_id BIGINT NOT NULL,
  student_id BIGINT NOT NULL,
  application_id BIGINT NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  allocated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_hostel_allocation_room FOREIGN KEY (hostel_room_id) REFERENCES hostel_rooms(id),
  CONSTRAINT fk_hostel_allocation_student FOREIGN KEY (student_id) REFERENCES students(id),
  CONSTRAINT fk_hostel_allocation_application FOREIGN KEY (application_id) REFERENCES hostel_applications(id),
  INDEX idx_hostel_allocation_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS suppliers (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  supplier_code VARCHAR(40) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  email VARCHAR(255),
  phone VARCHAR(40),
  active TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS procurement_requests (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  requester_id BIGINT NOT NULL,
  description VARCHAR(255) NOT NULL,
  estimated_amount DECIMAL(12,2) NOT NULL,
  supplier_id BIGINT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
  reviewed_by VARCHAR(255),
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_procurement_requester FOREIGN KEY (requester_id) REFERENCES users(id),
  CONSTRAINT fk_procurement_supplier FOREIGN KEY (supplier_id) REFERENCES suppliers(id),
  INDEX idx_procurement_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS assets (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  asset_tag VARCHAR(50) NOT NULL UNIQUE,
  name VARCHAR(200) NOT NULL,
  description VARCHAR(1000),
  purchase_value DECIMAL(12,2),
  purchase_date DATE,
  assigned_to_user_id BIGINT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT fk_asset_assignee FOREIGN KEY (assigned_to_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS research_projects (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL,
  title VARCHAR(255) NOT NULL,
  abstract_text VARCHAR(2000),
  supervisor_user_id BIGINT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED',
  reviewed_by VARCHAR(255),
  reviewed_at DATETIME NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_research_student FOREIGN KEY (student_id) REFERENCES students(id),
  CONSTRAINT fk_research_supervisor FOREIGN KEY (supervisor_user_id) REFERENCES users(id),
  INDEX idx_research_student_status (student_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS thesis_submissions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  research_project_id BIGINT NOT NULL,
  file_path VARCHAR(500) NOT NULL,
  notes VARCHAR(2000),
  status VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
  reviewed_by VARCHAR(255),
  reviewed_at DATETIME NULL,
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_thesis_research_project FOREIGN KEY (research_project_id) REFERENCES research_projects(id),
  INDEX idx_thesis_status_submitted (status, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS program_outcomes (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  program_id BIGINT NOT NULL,
  code VARCHAR(30) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1,
  CONSTRAINT uk_program_outcome_code UNIQUE (program_id, code),
  CONSTRAINT fk_program_outcome_program FOREIGN KEY (program_id) REFERENCES programs(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS accreditation_records (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  program_id BIGINT NOT NULL,
  accrediting_body VARCHAR(200) NOT NULL,
  certificate_reference VARCHAR(100),
  status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
  valid_from DATE NULL,
  valid_until DATE NULL,
  evidence_path VARCHAR(500),
  reviewed_by VARCHAR(255),
  reviewed_at DATETIME NULL,
  CONSTRAINT fk_accreditation_program FOREIGN KEY (program_id) REFERENCES programs(id),
  INDEX idx_accreditation_program_status (program_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS permissions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(80) NOT NULL UNIQUE,
  description VARCHAR(255) NOT NULL,
  active TINYINT(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS role_permissions (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  role_name VARCHAR(50) NOT NULL,
  permission_id BIGINT NOT NULL,
  CONSTRAINT uk_role_permission UNIQUE (role_name, permission_id),
  CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES permissions(id),
  INDEX idx_role_permission_role (role_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
