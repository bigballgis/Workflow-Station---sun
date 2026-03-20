-- =============================================================================
-- NOTE: This script is NO LONGER NEEDED for fresh initialization environments.
--
-- The root cause (P02) has been fixed directly in 01-insert-bpmn-process.sql:
-- Manager Approval node now correctly uses {{APPROVAL_FORM_ID}} instead of
-- {{REQUEST_FORM_ID}}.
--
-- This script is kept for backward compatibility with already-deployed
-- environments where sys_function_unit_contents may contain the old BPMN XML
-- with hardcoded formId=6 (Request Form) for the approval task.
--
-- For fresh init: this UPDATE will match 0 rows (no-op) since
-- sys_function_unit_contents won't have SIMPLE_APPROVAL data.
-- For deployed environments: it will fix the approval form reference.
-- =============================================================================
UPDATE sys_function_unit_contents fuc
SET content_data = REGEXP_REPLACE(
    content_data,
    '(<custom_1:values name="formId" value=")6(" />\r\n           <custom_1:values name="formName" value=")Request Form(" />\r\n           <custom_1:values name="formReadOnly")',
    '\17\2Approval Form\3',
    'g'
)
FROM sys_function_units fu
WHERE fuc.function_unit_id = fu.id
  AND fu.code = 'SIMPLE_APPROVAL'
  AND fu.version = '1.0.8'
  AND fuc.content_type = 'PROCESS';
