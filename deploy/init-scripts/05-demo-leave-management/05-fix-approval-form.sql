-- Fix Leave Approval Form
-- 1. Remove Approval Status field (should be set by action, not manually selected)
-- 2. Keep only read-only fields from application + approver comments

UPDATE dw_form_definitions
SET config_json = '{
  "size": "default",
  "layout": "vertical",
  "labelWidth": "120px",
  "rule": [
    {
      "type": "input",
      "field": "employeeName",
      "title": "Employee Name",
      "value": "",
      "props": {
        "disabled": true,
        "placeholder": "Employee name"
      },
      "col": {
        "span": 12
      }
    },
    {
      "type": "input",
      "field": "employeeId",
      "title": "Employee ID",
      "value": "",
      "props": {
        "disabled": true,
        "placeholder": "Employee ID"
      },
      "col": {
        "span": 12
      }
    },
    {
      "type": "select",
      "field": "leaveType",
      "title": "Leave Type",
      "value": "",
      "options": [
        {"label": "Annual Leave", "value": "ANNUAL"},
        {"label": "Sick Leave", "value": "SICK"},
        {"label": "Personal Leave", "value": "PERSONAL"},
        {"label": "Maternity Leave", "value": "MATERNITY"},
        {"label": "Paternity Leave", "value": "PATERNITY"}
      ],
      "props": {
        "disabled": true,
        "placeholder": "Leave type"
      },
      "col": {
        "span": 12
      }
    },
    {
      "type": "DatePicker",
      "field": "startDate",
      "title": "Start Date",
      "value": "",
      "props": {
        "type": "date",
        "disabled": true,
        "format": "YYYY-MM-DD",
        "valueFormat": "YYYY-MM-DD"
      },
      "col": {
        "span": 12
      }
    },
    {
      "type": "DatePicker",
      "field": "endDate",
      "title": "End Date",
      "value": "",
      "props": {
        "type": "date",
        "disabled": true,
        "format": "YYYY-MM-DD",
        "valueFormat": "YYYY-MM-DD"
      },
      "col": {
        "span": 12
      }
    },
    {
      "type": "inputNumber",
      "field": "totalDays",
      "title": "Total Days",
      "value": 0,
      "props": {
        "disabled": true
      },
      "col": {
        "span": 12
      }
    },
    {
      "type": "input",
      "field": "reason",
      "title": "Reason",
      "value": "",
      "props": {
        "type": "textarea",
        "rows": 4,
        "disabled": true,
        "placeholder": "Reason for leave"
      },
      "col": {
        "span": 24
      }
    },
    {
      "type": "input",
      "field": "approverComments",
      "title": "Approver Comments",
      "value": "",
      "props": {
        "type": "textarea",
        "rows": 4,
        "placeholder": "Enter your approval comments"
      },
      "validate": [
        {
          "required": true,
          "message": "Comments are required"
        }
      ],
      "col": {
        "span": 24
      }
    }
  ]
}'::jsonb
WHERE function_unit_id = 3 AND form_name = 'Leave Approval Form';

-- Verify the update
SELECT 
    id,
    form_name,
    jsonb_array_length(config_json->'rule') as field_count,
    LENGTH(config_json::text) as config_size
FROM dw_form_definitions
WHERE function_unit_id = 3 AND form_name = 'Leave Approval Form';
