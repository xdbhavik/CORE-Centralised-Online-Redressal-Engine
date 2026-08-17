-- Default priority data
INSERT INTO priority_master (priority_code, priority_name, is_active, display_order, is_deleted, created_at)
SELECT 'HIGH', 'High Priority', 1, 1, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM priority_master WHERE priority_code = 'HIGH');

INSERT INTO priority_master (priority_code, priority_name, is_active, display_order, is_deleted, created_at)
SELECT 'MEDIUM', 'Medium Priority', 1, 2, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM priority_master WHERE priority_code = 'MEDIUM');

INSERT INTO priority_master (priority_code, priority_name, is_active, display_order, is_deleted, created_at)
SELECT 'LOW', 'Low Priority', 1, 3, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM priority_master WHERE priority_code = 'LOW');

-- Default departments
INSERT INTO departments (department_name, description, is_active, is_deleted, created_at)
SELECT 'Public Works Department', 'Handles general public works and maintenance complaints.', 1, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE department_name = 'Public Works Department');

INSERT INTO departments (department_name, description, is_active, is_deleted, created_at)
SELECT 'Water Department', 'Handles drinking water supply, leakage, dirty water and pipeline issues.', 1, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE department_name = 'Water Department');

INSERT INTO departments (department_name, description, is_active, is_deleted, created_at)
SELECT 'Electricity Department', 'Handles power outage, transformer fault and street light complaints.', 1, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE department_name = 'Electricity Department');

INSERT INTO departments (department_name, description, is_active, is_deleted, created_at)
SELECT 'Road Department', 'Handles potholes, broken roads and road repair complaints.', 1, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE department_name = 'Road Department');

INSERT INTO departments (department_name, description, is_active, is_deleted, created_at)
SELECT 'Sanitation Department', 'Handles garbage collection, drainage blockage and sewer overflow complaints.', 1, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE department_name = 'Sanitation Department');

-- Default categories
INSERT INTO categories (category_name, department_id, default_priority_id, is_active, is_deleted, created_at)
SELECT 'Road Maintenance', d.department_id, p.priority_id, 1, 0, CURRENT_TIMESTAMP
FROM departments d, priority_master p
WHERE d.department_name = 'Public Works Department' AND p.priority_code = 'MEDIUM'
AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.category_name = 'Road Maintenance');

INSERT INTO categories (category_name, department_id, default_priority_id, is_active, is_deleted, created_at)
SELECT 'Water Supply', d.department_id, p.priority_id, 1, 0, CURRENT_TIMESTAMP
FROM departments d, priority_master p
WHERE d.department_name = 'Water Department' AND p.priority_code = 'HIGH'
AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.category_name = 'Water Supply');

INSERT INTO categories (category_name, department_id, default_priority_id, is_active, is_deleted, created_at)
SELECT 'Water Quality', d.department_id, p.priority_id, 1, 0, CURRENT_TIMESTAMP
FROM departments d, priority_master p
WHERE d.department_name = 'Water Department' AND p.priority_code = 'HIGH'
AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.category_name = 'Water Quality');

INSERT INTO categories (category_name, department_id, default_priority_id, is_active, is_deleted, created_at)
SELECT 'Power Supply', d.department_id, p.priority_id, 1, 0, CURRENT_TIMESTAMP
FROM departments d, priority_master p
WHERE d.department_name = 'Electricity Department' AND p.priority_code = 'HIGH'
AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.category_name = 'Power Supply');

INSERT INTO categories (category_name, department_id, default_priority_id, is_active, is_deleted, created_at)
SELECT 'Street Light', d.department_id, p.priority_id, 1, 0, CURRENT_TIMESTAMP
FROM departments d, priority_master p
WHERE d.department_name = 'Electricity Department' AND p.priority_code = 'MEDIUM'
AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.category_name = 'Street Light');

INSERT INTO categories (category_name, department_id, default_priority_id, is_active, is_deleted, created_at)
SELECT 'Road Repair', d.department_id, p.priority_id, 1, 0, CURRENT_TIMESTAMP
FROM departments d, priority_master p
WHERE d.department_name = 'Road Department' AND p.priority_code = 'MEDIUM'
AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.category_name = 'Road Repair');

INSERT INTO categories (category_name, department_id, default_priority_id, is_active, is_deleted, created_at)
SELECT 'Garbage Collection', d.department_id, p.priority_id, 1, 0, CURRENT_TIMESTAMP
FROM departments d, priority_master p
WHERE d.department_name = 'Sanitation Department' AND p.priority_code = 'MEDIUM'
AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.category_name = 'Garbage Collection');

INSERT INTO categories (category_name, department_id, default_priority_id, is_active, is_deleted, created_at)
SELECT 'Drainage', d.department_id, p.priority_id, 1, 0, CURRENT_TIMESTAMP
FROM departments d, priority_master p
WHERE d.department_name = 'Sanitation Department' AND p.priority_code = 'HIGH'
AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.category_name = 'Drainage');

-- Default status
INSERT INTO complaint_status (status_code, status_name, is_active, display_order, is_deleted, created_at)
SELECT 'REGISTERED', 'Complaint Registered', 1, 1, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM complaint_status WHERE status_code = 'REGISTERED');

INSERT INTO complaint_status (status_code, status_name, is_active, display_order, is_deleted, created_at)
SELECT 'AI_ANALYZED', 'AI Analysis Completed', 1, 2, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM complaint_status WHERE status_code = 'AI_ANALYZED');

INSERT INTO complaint_status (status_code, status_name, is_active, display_order, is_deleted, created_at)
SELECT 'ASSIGNED', 'Assigned to Officer', 1, 3, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM complaint_status WHERE status_code = 'ASSIGNED');

INSERT INTO complaint_status (status_code, status_name, is_active, display_order, is_deleted, created_at)
SELECT 'ACCEPTED', 'Accepted by Officer', 1, 4, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM complaint_status WHERE status_code = 'ACCEPTED');

INSERT INTO complaint_status (status_code, status_name, is_active, display_order, is_deleted, created_at)
SELECT 'IN_PROGRESS', 'Work In Progress', 1, 5, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM complaint_status WHERE status_code = 'IN_PROGRESS');

INSERT INTO complaint_status (status_code, status_name, is_active, display_order, is_deleted, created_at)
SELECT 'RESOLVED', 'Resolved', 1, 6, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM complaint_status WHERE status_code = 'RESOLVED');

INSERT INTO complaint_status (status_code, status_name, is_active, display_order, is_deleted, created_at)
SELECT 'CLOSED', 'Closed', 1, 7, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM complaint_status WHERE status_code = 'CLOSED');

INSERT INTO complaint_status (status_code, status_name, is_active, display_order, is_deleted, created_at)
SELECT 'CANCELLED', 'Cancelled by Citizen', 1, 8, 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM complaint_status WHERE status_code = 'CANCELLED');

-- System settings — assignment mode toggle (default: MANUAL, admin assigns officers)
INSERT INTO system_settings (setting_key, setting_value, description, is_deleted, created_at)
SELECT 'AUTO_ASSIGNMENT_ENABLED', 'false', 'When true, AI automatically assigns new complaints to the best-matched officer of the detected department. When false, complaints wait in the admin manual assignment queue.', 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'AUTO_ASSIGNMENT_ENABLED');

-- System settings — SLA resolution windows (hours) per priority.
-- SLA clock starts at complaint creation. Admin can tune these without a redeploy;
-- SlaService falls back to the same values in code if a row is missing.
INSERT INTO system_settings (setting_key, setting_value, description, is_deleted, created_at)
SELECT 'SLA_HIGH_HOURS', '24', 'SLA resolution window in hours for HIGH priority complaints.', 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'SLA_HIGH_HOURS');

INSERT INTO system_settings (setting_key, setting_value, description, is_deleted, created_at)
SELECT 'SLA_MEDIUM_HOURS', '72', 'SLA resolution window in hours for MEDIUM priority complaints (also the fallback for unknown priorities).', 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'SLA_MEDIUM_HOURS');

INSERT INTO system_settings (setting_key, setting_value, description, is_deleted, created_at)
SELECT 'SLA_LOW_HOURS', '120', 'SLA resolution window in hours for LOW priority complaints.', 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'SLA_LOW_HOURS');

INSERT INTO system_settings (setting_key, setting_value, description, is_deleted, created_at)
SELECT 'SLA_NEAR_BREACH_PERCENT', '80', 'Percentage of the SLA window after which an open complaint is flagged NEAR_BREACH and a reminder is sent.', 0, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM system_settings WHERE setting_key = 'SLA_NEAR_BREACH_PERCENT');

