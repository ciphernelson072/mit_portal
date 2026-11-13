-- Backward-compatible migration for requests submitted by teachers and students.
ALTER TABLE service_requests MODIFY student_id BIGINT NULL;
ALTER TABLE service_requests ADD COLUMN requester_id BIGINT NULL AFTER student_id;
ALTER TABLE service_requests ADD CONSTRAINT fk_service_request_requester
  FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX idx_service_request_requester ON service_requests(requester_id);
UPDATE service_requests sr
JOIN students s ON s.id = sr.student_id
SET sr.requester_id = s.user_id
WHERE sr.requester_id IS NULL;