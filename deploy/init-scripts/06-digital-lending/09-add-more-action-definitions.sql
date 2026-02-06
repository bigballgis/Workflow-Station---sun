-- 为Digital Lending流程的其他任务添加action定义
-- 日期: 2026-02-06

-- Credit Check任务的actions
INSERT INTO sys_action_definitions (id, function_unit_id, action_name, action_type, description, icon, button_color, config_json)
VALUES 
('action-dl-credit-check', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Perform Credit Check', 'APPROVE', 'Perform credit check on applicant', 'check-circle', 'primary', '{}'),
('action-dl-view-credit-report', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'View Credit Report', 'FORM_POPUP', 'View detailed credit report', 'file-text', 'info', '{"formId": 10}'),
('action-dl-calculate-emi', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Calculate EMI', 'FORM_POPUP', 'Calculate EMI for loan', 'calculator', 'warning', '{"formId": 11}');

-- Risk Assessment任务的actions
INSERT INTO sys_action_definitions (id, function_unit_id, action_name, action_type, description, icon, button_color, config_json)
VALUES 
('action-dl-assess-risk', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Assess Risk', 'APPROVE', 'Complete risk assessment', 'shield', 'primary', '{}'),
('action-dl-mark-high-risk', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Mark as High Risk', 'REJECT', 'Mark loan as high risk', 'exclamation-triangle', 'danger', '{}'),
('action-dl-mark-low-risk', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Mark as Low Risk', 'APPROVE', 'Mark loan as low risk', 'check', 'success', '{}');

-- Manager Approval任务的actions
INSERT INTO sys_action_definitions (id, function_unit_id, action_name, action_type, description, icon, button_color, config_json)
VALUES 
('action-dl-manager-approve', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Approve', 'APPROVE', 'Approve loan application', 'check', 'success', '{}'),
('action-dl-manager-reject', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Reject', 'REJECT', 'Reject loan application', 'times', 'danger', '{}'),
('action-dl-request-revision', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Request Revision', 'FORM_POPUP', 'Request revisions to application', 'edit', 'warning', '{"formId": 12}');

-- Senior Manager Approval任务的actions
INSERT INTO sys_action_definitions (id, function_unit_id, action_name, action_type, description, icon, button_color, config_json)
VALUES 
('action-dl-senior-approve', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Final Approve', 'APPROVE', 'Give final approval', 'check-circle', 'success', '{}'),
('action-dl-senior-reject', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Final Reject', 'REJECT', 'Give final rejection', 'times-circle', 'danger', '{}'),
('action-dl-escalate', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Escalate', 'FORM_POPUP', 'Escalate to board', 'arrow-up', 'warning', '{"formId": 13}');

-- Disbursement任务的actions
INSERT INTO sys_action_definitions (id, function_unit_id, action_name, action_type, description, icon, button_color, config_json)
VALUES 
('action-dl-disburse', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Disburse Loan', 'APPROVE', 'Process loan disbursement', 'money-bill', 'success', '{}'),
('action-dl-hold-disbursement', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Hold Disbursement', 'REJECT', 'Hold disbursement pending review', 'pause', 'warning', '{}'),
('action-dl-verify-account', 'a0cfc4cc-b42d-440a-886e-ac4ae1ffdb89', 'Verify Account', 'FORM_POPUP', 'Verify beneficiary account', 'bank', 'info', '{"formId": 14}');
