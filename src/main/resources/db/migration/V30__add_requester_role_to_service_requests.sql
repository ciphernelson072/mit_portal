-- Add requester_role column to service_requests table for tracking submission source
-- This allows differentiation between STUDENT, TEACHER, and ADMIN submissions
ALTER TABLE service_requests ADD COLUMN requester_role VARCHAR(50) DEFAULT 'STUDENT';

-- Create index on requester_role for efficient filtering by role
CREATE INDEX idx_service_request_requester_role ON service_requests(requester_role);
