-- Purchase Request - Action Configurations Update
-- Fix action types and config_json to match frontend expectations

-- Update SCRIPT to CUSTOM_SCRIPT and add proper script content
UPDATE dw_action_definitions 
SET action_type = 'CUSTOM_SCRIPT',
    config_json = '{"script": "// Calculate total amount from purchase items\nfunction calculateTotal(items) {\n  return items.reduce((sum, item) => sum + (item.quantity * item.unit_price), 0);\n}"}'::jsonb
WHERE id = 28 AND action_name = 'Calculate Amount';

UPDATE dw_action_definitions 
SET action_type = 'CUSTOM_SCRIPT',
    config_json = '{"script": "// Save current form data as draft\nfunction saveDraft(formData) {\n  localStorage.setItem(\"draft_\" + formData.request_no, JSON.stringify(formData));\n  return { success: true, message: \"Draft saved\" };\n}"}'::jsonb
WHERE id = 29 AND action_name = 'Save Draft';

-- Update APPROVE action with proper config
UPDATE dw_action_definitions 
SET config_json = '{"targetStatus": "APPROVED", "requireComment": true, "confirmMessage": "确定要批准此申请吗？"}'::jsonb
WHERE id = 22 AND action_name = 'Approve';

-- Update REJECT action with proper config
UPDATE dw_action_definitions 
SET config_json = '{"targetStatus": "REJECTED", "requireComment": true, "confirmMessage": "确定要拒绝此申请吗？"}'::jsonb
WHERE id = 23 AND action_name = 'Reject';

-- Update TRANSFER action with proper config
UPDATE dw_action_definitions 
SET config_json = '{"requireAssignee": true, "requireComment": true}'::jsonb
WHERE id = 24 AND action_name = 'Transfer';

-- Update ROLLBACK action with proper config
UPDATE dw_action_definitions 
SET config_json = '{"targetStep": "previous", "requireComment": true}'::jsonb
WHERE id = 25 AND action_name = 'Rollback';

-- Update WITHDRAW action with proper config
UPDATE dw_action_definitions 
SET config_json = '{"targetStatus": "CANCELLED", "allowedFromStatus": ["PENDING", "IN_PROGRESS"]}'::jsonb
WHERE id = 26 AND action_name = 'Withdraw';

-- Update PROCESS_SUBMIT action with proper config
UPDATE dw_action_definitions 
SET config_json = '{"requireComment": false, "confirmMessage": "确定要提交此申请吗？"}'::jsonb
WHERE id = 21 AND action_name = 'Submit';

-- Update API_CALL actions with proper config
UPDATE dw_action_definitions 
SET config_json = '{"url": "/api/budget/query", "method": "GET", "headers": "{\"Content-Type\": \"application/json\"}", "body": "{}"}'::jsonb
WHERE id = 27 AND action_name = 'Query Budget';

UPDATE dw_action_definitions 
SET config_json = '{"url": "/api/countersign/start", "method": "POST", "headers": "{\"Content-Type\": \"application/json\"}", "body": "{\"requestId\": \"${requestId}\"}"}'::jsonb
WHERE id = 30 AND action_name = 'Start Countersign';
