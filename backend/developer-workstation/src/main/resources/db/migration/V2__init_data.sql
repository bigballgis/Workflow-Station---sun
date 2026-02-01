-- =====================================================
-- Developer Workstation V2: Initial Data
-- Icons and Purchase Request Function Unit
-- =====================================================

-- =====================================================
-- 1. Icons
-- =====================================================
INSERT INTO dw_icons (id, name, category, svg_content, file_size, description, created_by, created_at, updated_at)
VALUES 
    (1, 'approval-check', 'APPROVAL', '<svg viewBox="0 0 24 24"><path d="M9 16L5 12l-1 1L9 19 21 7l-1-1z"/></svg>', 150, 'Approval icon', 'system', NOW(), NOW()),
    (2, 'document', 'APPROVAL', '<svg viewBox="0 0 24 24"><path d="M14 2H6v20h12V8l-6-6z"/></svg>', 200, 'Document icon', 'system', NOW(), NOW()),
    (3, 'user', 'CUSTOMER', '<svg viewBox="0 0 24 24"><path d="M12 12c2 0 4-2 4-4s-2-4-4-4-4 2-4 4 2 4 4 4z"/></svg>', 180, 'User icon', 'system', NOW(), NOW()),
    (4, 'settings', 'GENERAL', '<svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="3"/></svg>', 100, 'Settings icon', 'system', NOW(), NOW()),
    (5, 'folder', 'GENERAL', '<svg viewBox="0 0 24 24"><path d="M10 4H4v16h16V8h-8l-2-2z"/></svg>', 160, 'Folder icon', 'system', NOW(), NOW()),
    (6, 'credit-card', 'CREDIT', '<svg viewBox="0 0 24 24"><path d="M20 4H4v16h16V4zm0 10H4v-2h16v2z"/></svg>', 170, 'Credit card icon', 'system', NOW(), NOW()),
    (7, 'account', 'ACCOUNT', '<svg viewBox="0 0 24 24"><path d="M21 18v1H5V5h14v1h-9v12h9z"/></svg>', 220, 'Account icon', 'system', NOW(), NOW()),
    (8, 'payment', 'PAYMENT', '<svg viewBox="0 0 24 24"><path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10 10-4.5 10-10S17.5 2 12 2z"/></svg>', 140, 'Payment icon', 'system', NOW(), NOW()),
    (9, 'compliance', 'COMPLIANCE', '<svg viewBox="0 0 24 24"><path d="M12 1L3 5v6c0 5.5 4 11 9 12 5-1 9-6.5 9-12V5l-9-4z"/></svg>', 250, 'Compliance icon', 'system', NOW(), NOW()),
    (10, 'operation', 'OPERATION', '<svg viewBox="0 0 24 24"><path d="M3.5 18.5l6-6 4 4L22 7l-1-1-7 8-4-4L2 17z"/></svg>', 280, 'Operation icon', 'system', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Reset sequence
SELECT setval('dw_icons_id_seq', (SELECT MAX(id) FROM dw_icons));

-- =====================================================
-- 2. Function Unit: Purchase Request
-- =====================================================
INSERT INTO dw_function_units (id, code, name, description, icon_id, status, current_version, created_by, created_at, updated_at)
VALUES (
    3, 
    'fu-purchase-request', 
    '采购申请', 
    '采购申请流程，支持多级审批、金额分级、部门会签等功能，覆盖所有7种任务分配类型和8种动作类型',
    6,
    'PUBLISHED',
    '1.0.0',
    'system',
    NOW(),
    NOW()
)
ON CONFLICT (code) DO UPDATE SET 
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = NOW();

-- Reset sequence
SELECT setval('dw_function_units_id_seq', (SELECT MAX(id) FROM dw_function_units));

-- =====================================================
-- 3. Table Definitions
-- =====================================================
INSERT INTO dw_table_definitions (id, function_unit_id, table_name, table_type, description)
SELECT 11, f.id, 'purchase_request', 'MAIN', '采购申请主表'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_table_definitions (id, function_unit_id, table_name, table_type, description)
SELECT 12, f.id, 'purchase_item', 'SUB', '采购明细子表'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_table_definitions (id, function_unit_id, table_name, table_type, description)
SELECT 13, f.id, 'supplier_info', 'RELATION', '供应商信息'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_table_definitions (id, function_unit_id, table_name, table_type, description)
SELECT 14, f.id, 'budget_info', 'RELATION', '预算信息'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_table_definitions (id, function_unit_id, table_name, table_type, description)
SELECT 15, f.id, 'purchase_approval', 'ACTION', '审批记录'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_table_definitions (id, function_unit_id, table_name, table_type, description)
SELECT 16, f.id, 'countersign_record', 'ACTION', '会签记录'
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

-- Reset sequence
SELECT setval('dw_table_definitions_id_seq', (SELECT MAX(id) FROM dw_table_definitions));

-- =====================================================
-- 4. Field Definitions
-- =====================================================

-- Table 11: purchase_request (Main Table) - 18 fields
INSERT INTO dw_field_definitions (id, table_id, field_name, data_type, length, precision_value, scale, nullable, default_value, is_primary_key, is_unique, description, sort_order) VALUES
(147, 11, 'id', 'BIGINT', NULL, NULL, NULL, false, NULL, true, false, 'Primary Key', 0),
(93, 11, 'request_no', 'VARCHAR', 50, NULL, NULL, false, NULL, false, true, 'Request Number', 1),
(94, 11, 'title', 'VARCHAR', 200, NULL, NULL, false, NULL, false, false, 'Title', 2),
(100, 11, 'applicant', 'VARCHAR', 50, NULL, NULL, false, NULL, false, false, 'Applicant', 3),
(101, 11, 'department', 'VARCHAR', 100, NULL, NULL, false, NULL, false, false, 'Department', 4),
(102, 11, 'apply_date', 'DATE', NULL, NULL, NULL, false, NULL, false, false, 'Apply Date', 5),
(103, 11, 'purchase_type', 'VARCHAR', 50, NULL, NULL, false, NULL, false, false, 'Purchase Type', 6),
(104, 11, 'urgency', 'VARCHAR', 20, NULL, NULL, false, NULL, false, false, 'Urgency Level', 7),
(105, 11, 'total_amount', 'DECIMAL', NULL, 18, 2, false, NULL, false, false, 'Total Amount', 8),
(106, 11, 'currency', 'VARCHAR', 10, NULL, NULL, true, 'CNY', false, false, 'Currency', 9),
(107, 11, 'reason', 'TEXT', 2000, NULL, NULL, false, NULL, false, false, 'Purchase Reason', 10),
(108, 11, 'expected_delivery_date', 'DATE', NULL, NULL, NULL, true, NULL, false, false, 'Expected Delivery Date', 11),
(109, 11, 'delivery_address', 'VARCHAR', 500, NULL, NULL, true, NULL, false, false, 'Delivery Address', 12),
(110, 11, 'contact_person', 'VARCHAR', 50, NULL, NULL, true, NULL, false, false, 'Contact Person', 13),
(95, 11, 'contact_phone', 'VARCHAR', 20, NULL, NULL, true, NULL, false, false, 'Contact Phone', 14),
(96, 11, 'attachments', 'TEXT', NULL, NULL, NULL, true, NULL, false, false, 'Attachments', 15),
(97, 11, 'remarks', 'TEXT', 1000, NULL, NULL, true, NULL, false, false, 'Remarks', 16),
(114, 11, 'status', 'VARCHAR', 20, NULL, NULL, true, 'DRAFT', false, false, 'Status', 17)
ON CONFLICT (id) DO NOTHING;

-- Table 12: purchase_item (Sub Table) - 8 fields
INSERT INTO dw_field_definitions (id, table_id, field_name, data_type, length, precision_value, scale, nullable, default_value, is_primary_key, is_unique, description, sort_order) VALUES
(142, 12, 'request_id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'Reference to purchase_request', 0),
(115, 12, 'item_name', 'VARCHAR', 200, NULL, NULL, false, NULL, false, false, 'Item Name', 1),
(116, 12, 'specification', 'VARCHAR', 200, NULL, NULL, true, NULL, false, false, 'Specification', 2),
(117, 12, 'unit', 'VARCHAR', 20, NULL, NULL, true, NULL, false, false, 'Unit', 3),
(118, 12, 'quantity', 'INTEGER', NULL, NULL, NULL, false, NULL, false, false, 'Quantity', 4),
(119, 12, 'unit_price', 'DECIMAL', NULL, 18, 2, true, NULL, false, false, 'Unit Price', 5),
(120, 12, 'amount', 'DECIMAL', NULL, 18, 2, true, NULL, false, false, 'Amount', 6),
(121, 12, 'item_remarks', 'TEXT', 500, NULL, NULL, true, NULL, false, false, 'Remarks', 7)
ON CONFLICT (id) DO NOTHING;

-- Table 13: supplier_info (Relation Table) - 6 fields
INSERT INTO dw_field_definitions (id, table_id, field_name, data_type, length, precision_value, scale, nullable, default_value, is_primary_key, is_unique, description, sort_order) VALUES
(143, 13, 'request_id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'Reference to purchase_request', 0),
(122, 13, 'supplier_name', 'VARCHAR', 200, NULL, NULL, false, NULL, false, false, 'Supplier Name', 1),
(123, 13, 'supplier_code', 'VARCHAR', 50, NULL, NULL, true, NULL, false, false, 'Supplier Code', 2),
(124, 13, 'supplier_contact', 'VARCHAR', 50, NULL, NULL, true, NULL, false, false, 'Contact Person', 3),
(125, 13, 'supplier_phone', 'VARCHAR', 20, NULL, NULL, true, NULL, false, false, 'Contact Phone', 4),
(126, 13, 'supplier_address', 'VARCHAR', 500, NULL, NULL, true, NULL, false, false, 'Address', 5)
ON CONFLICT (id) DO NOTHING;

-- Table 14: budget_info (Relation Table) - 6 fields
INSERT INTO dw_field_definitions (id, table_id, field_name, data_type, length, precision_value, scale, nullable, default_value, is_primary_key, is_unique, description, sort_order) VALUES
(144, 14, 'request_id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'Reference to purchase_request', 0),
(127, 14, 'budget_code', 'VARCHAR', 50, NULL, NULL, false, NULL, false, false, 'Budget Code', 1),
(128, 14, 'budget_name', 'VARCHAR', 200, NULL, NULL, true, NULL, false, false, 'Budget Name', 2),
(129, 14, 'budget_amount', 'DECIMAL', NULL, 18, 2, true, NULL, false, false, 'Budget Amount', 3),
(130, 14, 'used_amount', 'DECIMAL', NULL, 18, 2, true, NULL, false, false, 'Used Amount', 4),
(131, 14, 'available_amount', 'DECIMAL', NULL, 18, 2, true, NULL, false, false, 'Available Amount', 5)
ON CONFLICT (id) DO NOTHING;

-- Table 15: purchase_approval (Action Table) - 6 fields
INSERT INTO dw_field_definitions (id, table_id, field_name, data_type, length, precision_value, scale, nullable, default_value, is_primary_key, is_unique, description, sort_order) VALUES
(145, 15, 'request_id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'Reference to purchase_request', 0),
(132, 15, 'approver', 'VARCHAR', 50, NULL, NULL, false, NULL, false, false, 'Approver', 1),
(133, 15, 'approve_time', 'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Approve Time', 2),
(134, 15, 'approve_result', 'VARCHAR', 20, NULL, NULL, false, NULL, false, false, 'Approve Result', 3),
(135, 15, 'approve_comment', 'TEXT', 1000, NULL, NULL, true, NULL, false, false, 'Approve Comment', 4),
(136, 15, 'task_name', 'VARCHAR', 100, NULL, NULL, true, NULL, false, false, 'Task Name', 5)
ON CONFLICT (id) DO NOTHING;

-- Table 16: countersign_record (Action Table) - 6 fields
INSERT INTO dw_field_definitions (id, table_id, field_name, data_type, length, precision_value, scale, nullable, default_value, is_primary_key, is_unique, description, sort_order) VALUES
(146, 16, 'request_id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'Reference to purchase_request', 0),
(137, 16, 'signer', 'VARCHAR', 50, NULL, NULL, false, NULL, false, false, 'Signer', 1),
(138, 16, 'signer_dept', 'VARCHAR', 100, NULL, NULL, true, NULL, false, false, 'Signer Department', 2),
(139, 16, 'sign_time', 'TIMESTAMP', NULL, NULL, NULL, true, NULL, false, false, 'Sign Time', 3),
(140, 16, 'sign_result', 'VARCHAR', 20, NULL, NULL, true, NULL, false, false, 'Sign Result', 4),
(141, 16, 'sign_comment', 'TEXT', 1000, NULL, NULL, true, NULL, false, false, 'Sign Comment', 5)
ON CONFLICT (id) DO NOTHING;

-- Reset sequence
SELECT setval('dw_field_definitions_id_seq', (SELECT MAX(id) FROM dw_field_definitions));

-- =====================================================
-- 5. Foreign Keys
-- =====================================================
INSERT INTO dw_foreign_keys (id, table_id, field_id, ref_table_id, ref_field_id, on_delete, on_update) VALUES
(3, 12, 142, 11, 147, 'CASCADE', 'CASCADE'),
(4, 13, 143, 11, 147, 'CASCADE', 'CASCADE'),
(5, 14, 144, 11, 147, 'CASCADE', 'CASCADE'),
(6, 15, 145, 11, 147, 'CASCADE', 'CASCADE'),
(7, 16, 146, 11, 147, 'CASCADE', 'CASCADE')
ON CONFLICT (id) DO NOTHING;

-- Reset sequence
SELECT setval('dw_foreign_keys_id_seq', (SELECT MAX(id) FROM dw_foreign_keys));

-- =====================================================
-- 6. Form Definitions
-- =====================================================
INSERT INTO dw_form_definitions (id, function_unit_id, form_name, form_type, config_json, description, bound_table_id)
SELECT 11, f.id, 'Purchase Request Main Form', 'MAIN', '{}'::jsonb, 'Main form for purchase request', NULL
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_form_definitions (id, function_unit_id, form_name, form_type, config_json, description, bound_table_id)
SELECT 12, f.id, 'Purchase Items Form', 'SUB', '{}'::jsonb, 'Sub form for purchase items', NULL
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_form_definitions (id, function_unit_id, form_name, form_type, config_json, description, bound_table_id)
SELECT 13, f.id, 'Approval Form', 'ACTION', '{}'::jsonb, 'Approval action form', NULL
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_form_definitions (id, function_unit_id, form_name, form_type, config_json, description, bound_table_id)
SELECT 14, f.id, 'Supplier Selection', 'POPUP', '{}'::jsonb, 'Supplier selection popup form', NULL
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_form_definitions (id, function_unit_id, form_name, form_type, config_json, description, bound_table_id)
SELECT 15, f.id, 'Budget Query', 'POPUP', '{}'::jsonb, 'Budget query popup form', NULL
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_form_definitions (id, function_unit_id, form_name, form_type, config_json, description, bound_table_id)
SELECT 16, f.id, 'Countersign Form', 'ACTION', '{}'::jsonb, 'Countersign action form', NULL
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

-- Reset sequence
SELECT setval('dw_form_definitions_id_seq', (SELECT MAX(id) FROM dw_form_definitions));

-- =====================================================
-- 7. Form-Table Bindings
-- =====================================================
INSERT INTO dw_form_table_bindings (id, form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order) VALUES
(8, 11, 11, 'PRIMARY', 'EDITABLE', NULL, 0),
(9, 11, 12, 'SUB', 'EDITABLE', 'request_id', 1),
(10, 11, 13, 'RELATED', 'EDITABLE', 'request_id', 2),
(11, 12, 12, 'PRIMARY', 'EDITABLE', NULL, 0),
(12, 13, 11, 'PRIMARY', 'READONLY', NULL, 0),
(13, 13, 15, 'SUB', 'EDITABLE', 'request_id', 1),
(14, 14, 13, 'PRIMARY', 'EDITABLE', NULL, 0),
(15, 15, 14, 'PRIMARY', 'READONLY', NULL, 0),
(16, 16, 11, 'PRIMARY', 'READONLY', NULL, 0),
(17, 16, 16, 'SUB', 'EDITABLE', 'request_id', 1)
ON CONFLICT (id) DO NOTHING;

-- Reset sequence
SELECT setval('dw_form_table_bindings_id_seq', (SELECT MAX(id) FROM dw_form_table_bindings));

-- =====================================================
-- 8. Action Definitions
-- =====================================================
INSERT INTO dw_action_definitions (id, function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT 21, f.id, 'Submit', 'PROCESS_SUBMIT', '{"confirmMessage": "确定要提交此申请吗？", "requireComment": false}'::jsonb, NULL, 'primary', 'Submit purchase request', true
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_action_definitions (id, function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT 22, f.id, 'Approve', 'APPROVE', '{"targetStatus": "APPROVED", "confirmMessage": "确定要批准此申请吗？", "requireComment": true}'::jsonb, NULL, 'success', 'Approve the request', true
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_action_definitions (id, function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT 23, f.id, 'Reject', 'REJECT', '{"targetStatus": "REJECTED", "confirmMessage": "确定要拒绝此申请吗？", "requireComment": true}'::jsonb, NULL, 'danger', 'Reject the request', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_action_definitions (id, function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT 24, f.id, 'Transfer', 'TRANSFER', '{"requireComment": true, "requireAssignee": true}'::jsonb, NULL, 'warning', 'Transfer to another person', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_action_definitions (id, function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT 25, f.id, 'Rollback', 'ROLLBACK', '{"targetStep": "previous", "requireComment": true}'::jsonb, NULL, 'warning', 'Rollback to previous step', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_action_definitions (id, function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT 26, f.id, 'Withdraw', 'WITHDRAW', '{"targetStatus": "CANCELLED", "allowedFromStatus": ["PENDING", "IN_PROGRESS"]}'::jsonb, NULL, 'default', 'Withdraw submitted request', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_action_definitions (id, function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT 27, f.id, 'Query Budget', 'API_CALL', '{"url": "/api/budget/query", "body": "{}", "method": "GET", "headers": "{\"Content-Type\": \"application/json\"}"}'::jsonb, NULL, 'info', 'Query budget information', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_action_definitions (id, function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT 28, f.id, 'Calculate Amount', 'CUSTOM_SCRIPT', '{"script": "// Calculate total amount from purchase items\nfunction calculateTotal(items) {\n  return items.reduce((sum, item) => sum + (item.quantity * item.unit_price), 0);\n}"}'::jsonb, NULL, 'default', 'Calculate total amount', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_action_definitions (id, function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT 29, f.id, 'Save Draft', 'CUSTOM_SCRIPT', '{"script": "// Save current form data as draft\nfunction saveDraft(formData) {\n  localStorage.setItem(\"draft_\" + formData.request_no, JSON.stringify(formData));\n  return { success: true, message: \"Draft saved\" };\n}"}'::jsonb, NULL, 'default', 'Save as draft', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

INSERT INTO dw_action_definitions (id, function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default)
SELECT 30, f.id, 'Start Countersign', 'API_CALL', '{"url": "/api/countersign/start", "body": "{\"requestId\": \"${requestId}\"}", "method": "POST", "headers": "{\"Content-Type\": \"application/json\"}"}'::jsonb, NULL, 'info', 'Start multi-department countersign', false
FROM dw_function_units f WHERE f.code = 'fu-purchase-request'
ON CONFLICT (id) DO NOTHING;

-- Reset sequence
SELECT setval('dw_action_definitions_id_seq', (SELECT MAX(id) FROM dw_action_definitions));

