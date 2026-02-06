-- Update form configurations with actual form fields
-- This adds proper form fields to the Leave Management demo

-- Update Leave Application Form with fields
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
        "placeholder": "Enter employee name"
      },
      "validate": [
        {
          "required": true,
          "message": "Please enter employee name",
          "trigger": "blur"
        }
      ],
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
        "placeholder": "Enter employee ID"
      },
      "validate": [
        {
          "required": true,
          "message": "Employee ID is required"
        }
      ],
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
        "placeholder": "Select leave type"
      },
      "validate": [
        {
          "required": true,
          "message": "Leave type is required"
        }
      ],
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
        "placeholder": "Select start date",
        "format": "YYYY-MM-DD",
        "valueFormat": "YYYY-MM-DD"
      },
      "validate": [
        {
          "required": true,
          "message": "Start date is required"
        }
      ],
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
        "placeholder": "Select end date",
        "format": "YYYY-MM-DD",
        "valueFormat": "YYYY-MM-DD"
      },
      "validate": [
        {
          "required": true,
          "message": "End date is required"
        }
      ],
      "col": {
        "span": 12
      }
    },
    {
      "type": "inputNumber",
      "field": "totalDays",
      "title": "Total Days",
      "value": 1,
      "props": {
        "min": 0.5,
        "max": 365,
        "step": 0.5,
        "precision": 1,
        "placeholder": "Enter total days"
      },
      "validate": [
        {
          "required": true,
          "message": "Total days is required"
        }
      ],
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
        "placeholder": "Enter reason for leave"
      },
      "validate": [
        {
          "required": true,
          "message": "Reason is required"
        }
      ],
      "col": {
        "span": 24
      }
    },
    {
      "type": "input",
      "field": "contactPhone",
      "title": "Contact Phone",
      "value": "",
      "props": {
        "placeholder": "Enter contact phone number"
      },
      "col": {
        "span": 12
      }
    },
    {
      "type": "input",
      "field": "emergencyContact",
      "title": "Emergency Contact",
      "value": "",
      "props": {
        "placeholder": "Enter emergency contact name"
      },
      "col": {
        "span": 12
      }
    }
  ]
}'::jsonb
WHERE function_unit_id = 3 AND form_name = 'Leave Application Form';

-- Update Leave Approval Form with fields
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
      "type": "select",
      "field": "approvalStatus",
      "title": "Approval Status",
      "value": "",
      "options": [
        {"label": "Approved", "value": "APPROVED"},
        {"label": "Rejected", "value": "REJECTED"},
        {"label": "Pending", "value": "PENDING"}
      ],
      "props": {
        "placeholder": "Select approval status"
      },
      "validate": [
        {
          "required": true,
          "message": "Approval status is required"
        }
      ],
      "col": {
        "span": 12
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
        "placeholder": "Enter approval comments"
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

-- Verify the updates
SELECT 
    id,
    form_name,
    jsonb_array_length(config_json->'rule') as field_count,
    LENGTH(config_json::text) as config_size
FROM dw_form_definitions
WHERE function_unit_id = 3
ORDER BY id;
