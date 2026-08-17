USE grievance_system;

-- Ensure priority_master has description column (only if not already present)
ALTER TABLE priority_master
ADD COLUMN IF NOT EXISTS description VARCHAR(255);

-- Insert Priority
INSERT IGNORE INTO priority_master (priority_code, description, is_active, display_order)
VALUES ('MEDIUM', 'Medium Priority', 1, 2);

-- Insert Department
INSERT IGNORE INTO departments (department_name, active)
VALUES ('Public Works Department', 1);

-- Insert Category
INSERT IGNORE INTO categories (category_name, department_id, default_priority_id, active)
SELECT 'Road Maintenance', d.department_id, p.priority_id, 1
FROM departments d, priority_master p
WHERE d.department_name = 'Public Works Department' AND p.priority_code = 'MEDIUM'
LIMIT 1;

-- Insert Status
INSERT IGNORE INTO complaint_status_master (status_code, description, is_active, display_order)
VALUES ('REGISTERED', 'Complaint Registered', 1, 1);
INSERT IGNORE INTO complaint_status_master (status_code, description, is_active, display_order)
VALUES ('ACCEPTED', 'Accepted by Officer', 1, 4);
ALTER TABLE priority_master ADD COLUMN description VARCHAR(255);

