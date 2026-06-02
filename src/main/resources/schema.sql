-- ============================================================================
-- School Information System - Enhanced Database Schema
-- Supports soft delete, CGPA calculations, profile images, and comprehensive data
-- ============================================================================

CREATE DATABASE IF NOT EXISTS sisdb
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE sisdb;

-- Users table: Core user accounts with soft-delete support
-- is_active field allows soft deletion without losing data
CREATE TABLE IF NOT EXISTS users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(255) NOT NULL UNIQUE COMMENT 'Unique username for login',
  password VARCHAR(255) NOT NULL COMMENT 'BCrypt encrypted password',
  role VARCHAR(50) NOT NULL COMMENT 'User role: ADMIN, TEACHER, STUDENT',
  email VARCHAR(255) UNIQUE COMMENT 'Email address for communication',
  phone VARCHAR(20) COMMENT 'Phone number for contact',
  is_active TINYINT(1) DEFAULT 1 COMMENT 'Soft delete flag: 1=active, 0=deleted',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_username (username),
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
CREATE TABLE IF NOT EXISTS courses (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL COMMENT 'Course name',
  teacher_id BIGINT NOT NULL COMMENT 'Foreign key to teachers table',
  semester VARCHAR(50) COMMENT 'Academic semester (e.g., Fall 2024, Spring 2024)',
  credits INT DEFAULT 3 COMMENT 'Credit hours for GPA calculations',
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_course_teacher FOREIGN KEY (teacher_id) REFERENCES teachers(id) ON DELETE RESTRICT,
  INDEX idx_semester (semester)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS course_students (
  course_id BIGINT NOT NULL COMMENT 'Foreign key to courses table',
  student_id BIGINT NOT NULL COMMENT 'Foreign key to students table',
  PRIMARY KEY (course_id, student_id),
  CONSTRAINT fk_cs_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  CONSTRAINT fk_cs_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Attendance table: Track student attendance in courses
-- Records daily attendance for audit and accountability
CREATE TABLE IF NOT EXISTS attendance (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id BIGINT NOT NULL COMMENT 'Foreign key to students table',
  course_id BIGINT NOT NULL COMMENT 'Foreign key to courses table',
  date DATE NOT NULL COMMENT 'Date of attendance record',
  present TINYINT(1) NOT NULL COMMENT '1=present, 0=absent',
  remarks VARCHAR(255) COMMENT 'Additional notes or reasons',
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_attendance_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  CONSTRAINT fk_attendance_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
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
  recorded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_grade_student FOREIGN KEY (student_id) REFERENCES students(id) ON DELETE CASCADE,
  CONSTRAINT fk_grade_course FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE CASCADE,
  INDEX idx_student (student_id),
  INDEX idx_course (course_id)
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
  INDEX idx_receiver (receiver_id),
  INDEX idx_read_status (read_status),
  INDEX idx_sent_at (sent_at)
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
