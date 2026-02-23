-- Fix Task_ManagerApproval to use Approval Form (id=7) instead of Request Form (id=6)
-- The approval task should show the Approval Form which has ApprovalActions sub-table

-- Update: replace formId=6/formName=Request Form with formId=7/formName=Approval Form
-- for the Task_ManagerApproval node only (identified by formReadOnly=true pattern)
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
