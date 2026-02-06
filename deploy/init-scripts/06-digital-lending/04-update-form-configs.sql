-- =============================================================================
-- Update Form Configurations with Field Definitions
-- This adds basic field configurations to make forms displayable
-- =============================================================================

-- Update Loan Application Form with basic fields
UPDATE dw_form_definitions
SET config_json = '{
    "layout": "vertical",
    "labelWidth": "150px",
    "size": "default",
    "rule": [
        {
            "type": "input",
            "field": "application_number",
            "title": "Application Number",
            "value": "",
            "props": {
                "placeholder": "Auto-generated"
            },
            "col": {"span": 12}
        },
        {
            "type": "select",
            "field": "loan_type",
            "title": "Loan Type",
            "value": "",
            "options": [
                {"label": "Personal Loan", "value": "Personal"},
                {"label": "Home Loan", "value": "Home"},
                {"label": "Auto Loan", "value": "Auto"},
                {"label": "Business Loan", "value": "Business"}
            ],
            "validate": [{"required": true, "message": "Loan type is required"}],
            "col": {"span": 12}
        },
        {
            "type": "inputNumber",
            "field": "loan_amount",
            "title": "Loan Amount",
            "value": 0,
            "props": {
                "min": 1000,
                "max": 10000000,
                "step": 1000,
                "placeholder": "Enter loan amount"
            },
            "validate": [{"required": true, "message": "Loan amount is required"}],
            "col": {"span": 12}
        },
        {
            "type": "inputNumber",
            "field": "loan_tenure_months",
            "title": "Tenure (Months)",
            "value": 12,
            "props": {
                "min": 6,
                "max": 360,
                "step": 6,
                "placeholder": "Enter tenure in months"
            },
            "validate": [{"required": true, "message": "Tenure is required"}],
            "col": {"span": 12}
        },
        {
            "type": "input",
            "field": "loan_purpose",
            "title": "Loan Purpose",
            "value": "",
            "props": {
                "type": "textarea",
                "rows": 3,
                "placeholder": "Enter purpose of loan"
            },
            "validate": [{"required": true, "message": "Purpose is required"}],
            "col": {"span": 24}
        }
    ]
}'::jsonb
WHERE form_name = 'Loan Application Form'
AND function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING');

-- Update Credit Check Form with basic fields
UPDATE dw_form_definitions
SET config_json = '{
    "layout": "vertical",
    "labelWidth": "150px",
    "size": "default",
    "width": "800px",
    "title": "Credit Bureau Check",
    "rule": [
        {
            "type": "input",
            "field": "bureau_name",
            "title": "Credit Bureau",
            "value": "",
            "props": {
                "placeholder": "e.g., Experian, Equifax"
            },
            "validate": [{"required": true, "message": "Bureau name is required"}],
            "col": {"span": 12}
        },
        {
            "type": "inputNumber",
            "field": "credit_score",
            "title": "Credit Score",
            "value": 0,
            "props": {
                "min": 300,
                "max": 900,
                "placeholder": "Enter credit score"
            },
            "validate": [{"required": true, "message": "Credit score is required"}],
            "col": {"span": 12}
        },
        {
            "type": "inputNumber",
            "field": "total_accounts",
            "title": "Total Accounts",
            "value": 0,
            "props": {
                "min": 0,
                "placeholder": "Total credit accounts"
            },
            "col": {"span": 12}
        },
        {
            "type": "inputNumber",
            "field": "active_accounts",
            "title": "Active Accounts",
            "value": 0,
            "props": {
                "min": 0,
                "placeholder": "Active credit accounts"
            },
            "col": {"span": 12}
        },
        {
            "type": "select",
            "field": "payment_history",
            "title": "Payment History",
            "value": "",
            "options": [
                {"label": "Excellent", "value": "Excellent"},
                {"label": "Good", "value": "Good"},
                {"label": "Fair", "value": "Fair"},
                {"label": "Poor", "value": "Poor"}
            ],
            "col": {"span": 12}
        },
        {
            "type": "input",
            "field": "remarks",
            "title": "Remarks",
            "value": "",
            "props": {
                "type": "textarea",
                "rows": 3,
                "placeholder": "Additional remarks"
            },
            "col": {"span": 24}
        }
    ]
}'::jsonb
WHERE form_name = 'Credit Check Form'
AND function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING');

-- Update Risk Assessment Form with basic fields
UPDATE dw_form_definitions
SET config_json = '{
    "layout": "vertical",
    "labelWidth": "150px",
    "size": "default",
    "width": "900px",
    "title": "Risk Assessment & Evaluation",
    "rule": [
        {
            "type": "select",
            "field": "risk_rating",
            "title": "Risk Rating",
            "value": "",
            "options": [
                {"label": "Low Risk", "value": "Low"},
                {"label": "Medium Risk", "value": "Medium"},
                {"label": "High Risk", "value": "High"}
            ],
            "validate": [{"required": true, "message": "Risk rating is required"}],
            "col": {"span": 12}
        },
        {
            "type": "inputNumber",
            "field": "credit_score",
            "title": "Credit Score",
            "value": 0,
            "props": {
                "disabled": true,
                "placeholder": "From credit check"
            },
            "col": {"span": 12}
        },
        {
            "type": "input",
            "field": "assessment_comments",
            "title": "Assessment Comments",
            "value": "",
            "props": {
                "type": "textarea",
                "rows": 4,
                "placeholder": "Enter risk assessment comments"
            },
            "validate": [{"required": true, "message": "Comments are required"}],
            "col": {"span": 24}
        }
    ]
}'::jsonb
WHERE form_name = 'Risk Assessment Form'
AND function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING');

-- Update Loan Approval Form with basic fields
UPDATE dw_form_definitions
SET config_json = '{
    "layout": "vertical",
    "labelWidth": "150px",
    "size": "default",
    "rule": [
        {
            "type": "input",
            "field": "application_number",
            "title": "Application Number",
            "value": "",
            "props": {
                "disabled": true
            },
            "col": {"span": 12}
        },
        {
            "type": "inputNumber",
            "field": "loan_amount",
            "title": "Loan Amount",
            "value": 0,
            "props": {
                "disabled": true
            },
            "col": {"span": 12}
        },
        {
            "type": "input",
            "field": "risk_rating",
            "title": "Risk Rating",
            "value": "",
            "props": {
                "disabled": true
            },
            "col": {"span": 12}
        },
        {
            "type": "inputNumber",
            "field": "credit_score",
            "title": "Credit Score",
            "value": 0,
            "props": {
                "disabled": true
            },
            "col": {"span": 12}
        },
        {
            "type": "input",
            "field": "approval_comments",
            "title": "Approval Comments",
            "value": "",
            "props": {
                "type": "textarea",
                "rows": 4,
                "placeholder": "Enter approval/rejection comments"
            },
            "validate": [{"required": true, "message": "Comments are required"}],
            "col": {"span": 24}
        }
    ]
}'::jsonb
WHERE form_name = 'Loan Approval Form'
AND function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING');

-- Update Loan Disbursement Form with basic fields
UPDATE dw_form_definitions
SET config_json = '{
    "layout": "vertical",
    "labelWidth": "150px",
    "size": "default",
    "rule": [
        {
            "type": "input",
            "field": "application_number",
            "title": "Application Number",
            "value": "",
            "props": {
                "disabled": true
            },
            "col": {"span": 12}
        },
        {
            "type": "inputNumber",
            "field": "loan_amount",
            "title": "Approved Amount",
            "value": 0,
            "props": {
                "disabled": true
            },
            "col": {"span": 12}
        },
        {
            "type": "DatePicker",
            "field": "disbursement_date",
            "title": "Disbursement Date",
            "value": "",
            "props": {
                "type": "date",
                "format": "YYYY-MM-DD",
                "valueFormat": "YYYY-MM-DD",
                "placeholder": "Select disbursement date"
            },
            "validate": [{"required": true, "message": "Disbursement date is required"}],
            "col": {"span": 12}
        },
        {
            "type": "input",
            "field": "disbursement_method",
            "title": "Disbursement Method",
            "value": "",
            "props": {
                "placeholder": "e.g., Bank Transfer, Check"
            },
            "validate": [{"required": true, "message": "Method is required"}],
            "col": {"span": 12}
        },
        {
            "type": "input",
            "field": "disbursement_notes",
            "title": "Notes",
            "value": "",
            "props": {
                "type": "textarea",
                "rows": 3,
                "placeholder": "Enter disbursement notes"
            },
            "col": {"span": 24}
        }
    ]
}'::jsonb
WHERE form_name = 'Loan Disbursement Form'
AND function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING');

-- Verify updates
SELECT 
    form_name,
    form_type,
    CASE 
        WHEN config_json ? 'rule' THEN 'Has fields'
        ELSE 'No fields'
    END as field_status,
    jsonb_array_length(config_json->'rule') as field_count
FROM dw_form_definitions
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING')
ORDER BY id;
