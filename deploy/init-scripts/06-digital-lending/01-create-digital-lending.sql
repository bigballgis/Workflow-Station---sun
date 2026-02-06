-- =============================================================================
-- Digital Lending Workflow System
-- A comprehensive loan application and approval system with all features
-- Including: Form Popup Actions, Multiple Tables, Complex Workflow, etc.
-- =============================================================================

DO $$
DECLARE
    v_function_unit_id BIGINT;
    v_loan_application_table_id BIGINT;
    v_applicant_info_table_id BIGINT;
    v_financial_info_table_id BIGINT;
    v_collateral_table_id BIGINT;
    v_credit_check_table_id BIGINT;
    v_approval_history_table_id BIGINT;
    v_documents_table_id BIGINT;
    
    v_application_form_id BIGINT;
    v_credit_check_form_id BIGINT;
    v_risk_assessment_form_id BIGINT;
    v_approval_form_id BIGINT;
    v_disbursement_form_id BIGINT;
    
    v_process_id BIGINT;
    
    v_submit_action_id BIGINT;
    v_credit_check_action_id BIGINT;
    v_approve_action_id BIGINT;
    v_reject_action_id BIGINT;
    v_request_info_action_id BIGINT;
    v_view_credit_action_id BIGINT;
    v_calculate_emi_action_id BIGINT;
BEGIN
    -- =============================================================================
    -- 1. Create Function Unit
    -- =============================================================================
    INSERT INTO public.dw_function_units (
        code,
        name,
        description,
        status,
        current_version,
        created_by,
        created_at,
        updated_at
    ) VALUES (
        'DIGITAL_LENDING',
        'Digital Lending System',
        'Comprehensive digital loan application and approval workflow with credit checks, risk assessment, collateral management, and automated disbursement',
        'DRAFT',
        '1.0.0',
        'system',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ) RETURNING id INTO v_function_unit_id;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'Created Function Unit: Digital Lending System';
    RAISE NOTICE 'Function Unit ID: %', v_function_unit_id;
    RAISE NOTICE '========================================';

    -- =============================================================================
    -- 2. Create Tables
    -- =============================================================================
    
    -- 2.1 Main Table: Loan Application
    INSERT INTO public.dw_table_definitions (
        function_unit_id,
        table_name,
        table_type,
        description
    ) VALUES (
        v_function_unit_id,
        'Loan Application',
        'MAIN',
        'Main loan application record'
    ) RETURNING id INTO v_loan_application_table_id;

    INSERT INTO public.dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_loan_application_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_loan_application_table_id, 'application_number', 'VARCHAR', 50, NULL, FALSE, 'Unique application number', 2),
    (v_loan_application_table_id, 'application_date', 'TIMESTAMP', NULL, NULL, FALSE, 'Application submission date', 3),
    (v_loan_application_table_id, 'loan_type', 'VARCHAR', 50, NULL, FALSE, 'Type of loan (Personal/Home/Auto/Business)', 4),
    (v_loan_application_table_id, 'loan_amount', 'DECIMAL', 15, 2, FALSE, 'Requested loan amount', 5),
    (v_loan_application_table_id, 'loan_tenure_months', 'INTEGER', NULL, NULL, FALSE, 'Loan tenure in months', 6),
    (v_loan_application_table_id, 'interest_rate', 'DECIMAL', 5, 2, TRUE, 'Annual interest rate (%)', 7),
    (v_loan_application_table_id, 'emi_amount', 'DECIMAL', 15, 2, TRUE, 'Monthly EMI amount', 8),
    (v_loan_application_table_id, 'loan_purpose', 'TEXT', NULL, NULL, FALSE, 'Purpose of the loan', 9),
    (v_loan_application_table_id, 'status', 'VARCHAR', 30, NULL, FALSE, 'Application status', 10),
    (v_loan_application_table_id, 'current_stage', 'VARCHAR', 50, NULL, TRUE, 'Current workflow stage', 11),
    (v_loan_application_table_id, 'risk_rating', 'VARCHAR', 20, NULL, TRUE, 'Risk rating (Low/Medium/High)', 12),
    (v_loan_application_table_id, 'credit_score', 'INTEGER', NULL, NULL, TRUE, 'Applicant credit score', 13),
    (v_loan_application_table_id, 'approval_date', 'TIMESTAMP', NULL, NULL, TRUE, 'Date of approval', 14),
    (v_loan_application_table_id, 'disbursement_date', 'TIMESTAMP', NULL, NULL, TRUE, 'Date of disbursement', 15),
    (v_loan_application_table_id, 'rejection_reason', 'TEXT', NULL, NULL, TRUE, 'Reason for rejection', 16),
    (v_loan_application_table_id, 'created_by', 'VARCHAR', 100, NULL, FALSE, 'Created by user', 17),
    (v_loan_application_table_id, 'created_at', 'TIMESTAMP', NULL, NULL, FALSE, 'Created timestamp', 18),
    (v_loan_application_table_id, 'updated_at', 'TIMESTAMP', NULL, NULL, TRUE, 'Updated timestamp', 19);

    RAISE NOTICE 'Created Main Table: Loan Application (ID: %)', v_loan_application_table_id;

    -- 2.2 Sub Table: Applicant Information
    INSERT INTO public.dw_table_definitions (
        function_unit_id,
        table_name,
        table_type,
        description
    ) VALUES (
        v_function_unit_id,
        'Applicant Information',
        'SUB',
        'Personal information of loan applicant'
    ) RETURNING id INTO v_applicant_info_table_id;

    INSERT INTO public.dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_applicant_info_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_applicant_info_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, 'FK to loan application', 2),
    (v_applicant_info_table_id, 'applicant_type', 'VARCHAR', 20, NULL, FALSE, 'Primary or Co-applicant', 3),
    (v_applicant_info_table_id, 'full_name', 'VARCHAR', 200, NULL, FALSE, 'Full legal name', 4),
    (v_applicant_info_table_id, 'date_of_birth', 'DATE', NULL, NULL, FALSE, 'Date of birth', 5),
    (v_applicant_info_table_id, 'gender', 'VARCHAR', 20, NULL, FALSE, 'Gender', 6),
    (v_applicant_info_table_id, 'marital_status', 'VARCHAR', 20, NULL, FALSE, 'Marital status', 7),
    (v_applicant_info_table_id, 'nationality', 'VARCHAR', 50, NULL, FALSE, 'Nationality', 8),
    (v_applicant_info_table_id, 'id_type', 'VARCHAR', 50, NULL, FALSE, 'ID document type', 9),
    (v_applicant_info_table_id, 'id_number', 'VARCHAR', 50, NULL, FALSE, 'ID document number', 10),
    (v_applicant_info_table_id, 'mobile_number', 'VARCHAR', 20, NULL, FALSE, 'Mobile phone number', 11),
    (v_applicant_info_table_id, 'email', 'VARCHAR', 100, NULL, FALSE, 'Email address', 12),
    (v_applicant_info_table_id, 'current_address', 'TEXT', NULL, NULL, FALSE, 'Current residential address', 13),
    (v_applicant_info_table_id, 'permanent_address', 'TEXT', NULL, NULL, TRUE, 'Permanent address', 14),
    (v_applicant_info_table_id, 'years_at_current_address', 'INTEGER', NULL, NULL, TRUE, 'Years at current address', 15);

    RAISE NOTICE 'Created Sub Table: Applicant Information (ID: %)', v_applicant_info_table_id;

    -- 2.3 Sub Table: Financial Information
    INSERT INTO public.dw_table_definitions (
        function_unit_id,
        table_name,
        table_type,
        description
    ) VALUES (
        v_function_unit_id,
        'Financial Information',
        'SUB',
        'Financial details of applicant'
    ) RETURNING id INTO v_financial_info_table_id;

    INSERT INTO public.dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_financial_info_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_financial_info_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, 'FK to loan application', 2),
    (v_financial_info_table_id, 'employment_type', 'VARCHAR', 50, NULL, FALSE, 'Employment type (Salaried/Self-employed/Business)', 3),
    (v_financial_info_table_id, 'employer_name', 'VARCHAR', 200, NULL, TRUE, 'Name of employer/company', 4),
    (v_financial_info_table_id, 'occupation', 'VARCHAR', 100, NULL, FALSE, 'Occupation/Job title', 5),
    (v_financial_info_table_id, 'years_of_employment', 'INTEGER', NULL, NULL, TRUE, 'Years in current employment', 6),
    (v_financial_info_table_id, 'monthly_income', 'DECIMAL', 15, 2, FALSE, 'Monthly gross income', 7),
    (v_financial_info_table_id, 'other_income', 'DECIMAL', 15, 2, TRUE, 'Other monthly income', 8),
    (v_financial_info_table_id, 'monthly_expenses', 'DECIMAL', 15, 2, FALSE, 'Monthly expenses', 9),
    (v_financial_info_table_id, 'existing_loans', 'DECIMAL', 15, 2, TRUE, 'Total existing loan obligations', 10),
    (v_financial_info_table_id, 'existing_emi', 'DECIMAL', 15, 2, TRUE, 'Total existing EMI payments', 11),
    (v_financial_info_table_id, 'bank_name', 'VARCHAR', 100, NULL, FALSE, 'Primary bank name', 12),
    (v_financial_info_table_id, 'account_number', 'VARCHAR', 50, NULL, FALSE, 'Bank account number', 13),
    (v_financial_info_table_id, 'account_type', 'VARCHAR', 30, NULL, FALSE, 'Account type (Savings/Current)', 14);

    RAISE NOTICE 'Created Sub Table: Financial Information (ID: %)', v_financial_info_table_id;

    -- 2.4 Sub Table: Collateral Details
    INSERT INTO public.dw_table_definitions (
        function_unit_id,
        table_name,
        table_type,
        description
    ) VALUES (
        v_function_unit_id,
        'Collateral Details',
        'SUB',
        'Details of collateral/security for secured loans'
    ) RETURNING id INTO v_collateral_table_id;

    INSERT INTO public.dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_collateral_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_collateral_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, 'FK to loan application', 2),
    (v_collateral_table_id, 'collateral_type', 'VARCHAR', 50, NULL, FALSE, 'Type (Property/Vehicle/Securities/FD)', 3),
    (v_collateral_table_id, 'collateral_description', 'TEXT', NULL, NULL, FALSE, 'Detailed description', 4),
    (v_collateral_table_id, 'estimated_value', 'DECIMAL', 15, 2, FALSE, 'Estimated market value', 5),
    (v_collateral_table_id, 'valuation_date', 'DATE', NULL, NULL, TRUE, 'Date of valuation', 6),
    (v_collateral_table_id, 'valuer_name', 'VARCHAR', 100, NULL, TRUE, 'Name of valuer', 7),
    (v_collateral_table_id, 'ownership_proof', 'VARCHAR', 200, NULL, TRUE, 'Ownership document reference', 8),
    (v_collateral_table_id, 'encumbrance_status', 'VARCHAR', 50, NULL, TRUE, 'Encumbrance status', 9);

    RAISE NOTICE 'Created Sub Table: Collateral Details (ID: %)', v_collateral_table_id;

    -- 2.5 Related Table: Credit Check Results
    INSERT INTO public.dw_table_definitions (
        function_unit_id,
        table_name,
        table_type,
        description
    ) VALUES (
        v_function_unit_id,
        'Credit Check Results',
        'RELATION',
        'Credit bureau check results'
    ) RETURNING id INTO v_credit_check_table_id;

    INSERT INTO public.dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_credit_check_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_credit_check_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, 'FK to loan application', 2),
    (v_credit_check_table_id, 'bureau_name', 'VARCHAR', 100, NULL, FALSE, 'Credit bureau name', 3),
    (v_credit_check_table_id, 'check_date', 'TIMESTAMP', NULL, NULL, FALSE, 'Date of credit check', 4),
    (v_credit_check_table_id, 'credit_score', 'INTEGER', NULL, NULL, FALSE, 'Credit score', 5),
    (v_credit_check_table_id, 'score_range', 'VARCHAR', 50, NULL, TRUE, 'Score range (e.g., 300-900)', 6),
    (v_credit_check_table_id, 'credit_history_length', 'INTEGER', NULL, NULL, TRUE, 'Credit history in months', 7),
    (v_credit_check_table_id, 'total_accounts', 'INTEGER', NULL, NULL, TRUE, 'Total credit accounts', 8),
    (v_credit_check_table_id, 'active_accounts', 'INTEGER', NULL, NULL, TRUE, 'Active credit accounts', 9),
    (v_credit_check_table_id, 'delinquent_accounts', 'INTEGER', NULL, NULL, TRUE, 'Delinquent accounts', 10),
    (v_credit_check_table_id, 'total_debt', 'DECIMAL', 15, 2, TRUE, 'Total outstanding debt', 11),
    (v_credit_check_table_id, 'credit_utilization', 'DECIMAL', 5, 2, TRUE, 'Credit utilization ratio (%)', 12),
    (v_credit_check_table_id, 'payment_history', 'VARCHAR', 20, NULL, TRUE, 'Payment history rating', 13),
    (v_credit_check_table_id, 'remarks', 'TEXT', NULL, NULL, TRUE, 'Additional remarks', 14);

    RAISE NOTICE 'Created Related Table: Credit Check Results (ID: %)', v_credit_check_table_id;

    -- 2.6 Related Table: Approval History
    INSERT INTO public.dw_table_definitions (
        function_unit_id,
        table_name,
        table_type,
        description
    ) VALUES (
        v_function_unit_id,
        'Approval History',
        'RELATION',
        'History of all approval/rejection actions'
    ) RETURNING id INTO v_approval_history_table_id;

    INSERT INTO public.dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_approval_history_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_approval_history_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, 'FK to loan application', 2),
    (v_approval_history_table_id, 'stage_name', 'VARCHAR', 100, NULL, FALSE, 'Approval stage name', 3),
    (v_approval_history_table_id, 'approver_name', 'VARCHAR', 100, NULL, FALSE, 'Name of approver', 4),
    (v_approval_history_table_id, 'approver_role', 'VARCHAR', 50, NULL, FALSE, 'Role of approver', 5),
    (v_approval_history_table_id, 'action', 'VARCHAR', 30, NULL, FALSE, 'Action taken', 6),
    (v_approval_history_table_id, 'decision', 'VARCHAR', 20, NULL, FALSE, 'Approve/Reject/Return', 7),
    (v_approval_history_table_id, 'action_date', 'TIMESTAMP', NULL, NULL, FALSE, 'Date of action', 8),
    (v_approval_history_table_id, 'comments', 'TEXT', NULL, NULL, TRUE, 'Approver comments', 9),
    (v_approval_history_table_id, 'conditions', 'TEXT', NULL, NULL, TRUE, 'Approval conditions', 10);

    RAISE NOTICE 'Created Related Table: Approval History (ID: %)', v_approval_history_table_id;

    -- 2.7 Related Table: Documents
    INSERT INTO public.dw_table_definitions (
        function_unit_id,
        table_name,
        table_type,
        description
    ) VALUES (
        v_function_unit_id,
        'Documents',
        'RELATION',
        'Supporting documents for loan application'
    ) RETURNING id INTO v_documents_table_id;

    INSERT INTO public.dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_documents_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_documents_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, 'FK to loan application', 2),
    (v_documents_table_id, 'document_type', 'VARCHAR', 100, NULL, FALSE, 'Type of document', 3),
    (v_documents_table_id, 'document_name', 'VARCHAR', 200, NULL, FALSE, 'Document name', 4),
    (v_documents_table_id, 'file_path', 'VARCHAR', 500, NULL, FALSE, 'File storage path', 5),
    (v_documents_table_id, 'file_size', 'BIGINT', NULL, NULL, TRUE, 'File size in bytes', 6),
    (v_documents_table_id, 'upload_date', 'TIMESTAMP', NULL, NULL, FALSE, 'Upload date', 7),
    (v_documents_table_id, 'uploaded_by', 'VARCHAR', 100, NULL, FALSE, 'Uploaded by user', 8),
    (v_documents_table_id, 'verification_status', 'VARCHAR', 30, NULL, TRUE, 'Verification status', 9),
    (v_documents_table_id, 'verified_by', 'VARCHAR', 100, NULL, TRUE, 'Verified by user', 10),
    (v_documents_table_id, 'verification_date', 'TIMESTAMP', NULL, NULL, TRUE, 'Verification date', 11);

    RAISE NOTICE 'Created Related Table: Documents (ID: %)', v_documents_table_id;

    -- =============================================================================
    -- 3. Create Forms
    -- =============================================================================
    
    -- 3.1 Loan Application Form
    INSERT INTO public.dw_form_definitions (
        function_unit_id,
        form_name,
        form_type,
        config_json,
        description,
        bound_table_id
    ) VALUES (
        v_function_unit_id,
        'Loan Application Form',
        'MAIN',
        '{
            "layout": "vertical",
            "labelWidth": "150px",
            "size": "default",
            "showSubmitButton": true,
            "submitButtonText": "Submit Application"
        }'::jsonb,
        'Complete loan application form for customers',
        v_loan_application_table_id
    ) RETURNING id INTO v_application_form_id;

    -- Bind tables to application form
    INSERT INTO public.dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, sort_order
    ) VALUES
    (v_application_form_id, v_loan_application_table_id, 'PRIMARY', 'EDITABLE', 1),
    (v_application_form_id, v_applicant_info_table_id, 'SUB', 'EDITABLE', 2),
    (v_application_form_id, v_financial_info_table_id, 'SUB', 'EDITABLE', 3),
    (v_application_form_id, v_collateral_table_id, 'SUB', 'EDITABLE', 4),
    (v_application_form_id, v_documents_table_id, 'RELATED', 'EDITABLE', 5);

    RAISE NOTICE 'Created Form: Loan Application Form (ID: %)', v_application_form_id;

    -- 3.2 Credit Check Form
    INSERT INTO public.dw_form_definitions (
        function_unit_id,
        form_name,
        form_type,
        config_json,
        description,
        bound_table_id
    ) VALUES (
        v_function_unit_id,
        'Credit Check Form',
        'POPUP',
        '{
            "layout": "vertical",
            "labelWidth": "150px",
            "size": "default",
            "width": "800px",
            "title": "Credit Bureau Check"
        }'::jsonb,
        'Form for credit officers to record credit check results',
        v_credit_check_table_id
    ) RETURNING id INTO v_credit_check_form_id;

    INSERT INTO public.dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, sort_order
    ) VALUES
    (v_credit_check_form_id, v_loan_application_table_id, 'PRIMARY', 'READONLY', 1),
    (v_credit_check_form_id, v_credit_check_table_id, 'RELATED', 'EDITABLE', 2);

    RAISE NOTICE 'Created Form: Credit Check Form (ID: %)', v_credit_check_form_id;

    -- 3.3 Risk Assessment Form
    INSERT INTO public.dw_form_definitions (
        function_unit_id,
        form_name,
        form_type,
        config_json,
        description,
        bound_table_id
    ) VALUES (
        v_function_unit_id,
        'Risk Assessment Form',
        'POPUP',
        '{
            "layout": "vertical",
            "labelWidth": "150px",
            "size": "default",
            "width": "900px",
            "title": "Risk Assessment & Evaluation"
        }'::jsonb,
        'Form for risk officers to assess loan risk',
        v_loan_application_table_id
    ) RETURNING id INTO v_risk_assessment_form_id;

    INSERT INTO public.dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, sort_order
    ) VALUES
    (v_risk_assessment_form_id, v_loan_application_table_id, 'PRIMARY', 'EDITABLE', 1),
    (v_risk_assessment_form_id, v_applicant_info_table_id, 'SUB', 'READONLY', 2),
    (v_risk_assessment_form_id, v_financial_info_table_id, 'SUB', 'READONLY', 3),
    (v_risk_assessment_form_id, v_credit_check_table_id, 'RELATED', 'READONLY', 4);

    RAISE NOTICE 'Created Form: Risk Assessment Form (ID: %)', v_risk_assessment_form_id;

    -- 3.4 Approval Form
    INSERT INTO public.dw_form_definitions (
        function_unit_id,
        form_name,
        form_type,
        config_json,
        description,
        bound_table_id
    ) VALUES (
        v_function_unit_id,
        'Loan Approval Form',
        'MAIN',
        '{
            "layout": "vertical",
            "labelWidth": "150px",
            "size": "default",
            "readonly": false
        }'::jsonb,
        'Form for managers to approve/reject loan applications',
        v_loan_application_table_id
    ) RETURNING id INTO v_approval_form_id;

    INSERT INTO public.dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, sort_order
    ) VALUES
    (v_approval_form_id, v_loan_application_table_id, 'PRIMARY', 'READONLY', 1),
    (v_approval_form_id, v_applicant_info_table_id, 'SUB', 'READONLY', 2),
    (v_approval_form_id, v_financial_info_table_id, 'SUB', 'READONLY', 3),
    (v_approval_form_id, v_collateral_table_id, 'SUB', 'READONLY', 4),
    (v_approval_form_id, v_credit_check_table_id, 'RELATED', 'READONLY', 5),
    (v_approval_form_id, v_approval_history_table_id, 'RELATED', 'EDITABLE', 6);

    RAISE NOTICE 'Created Form: Loan Approval Form (ID: %)', v_approval_form_id;

    -- 3.5 Disbursement Form
    INSERT INTO public.dw_form_definitions (
        function_unit_id,
        form_name,
        form_type,
        config_json,
        description,
        bound_table_id
    ) VALUES (
        v_function_unit_id,
        'Loan Disbursement Form',
        'MAIN',
        '{
            "layout": "vertical",
            "labelWidth": "150px",
            "size": "default"
        }'::jsonb,
        'Form for finance team to process loan disbursement',
        v_loan_application_table_id
    ) RETURNING id INTO v_disbursement_form_id;

    INSERT INTO public.dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, sort_order
    ) VALUES
    (v_disbursement_form_id, v_loan_application_table_id, 'PRIMARY', 'EDITABLE', 1),
    (v_disbursement_form_id, v_applicant_info_table_id, 'SUB', 'READONLY', 2),
    (v_disbursement_form_id, v_financial_info_table_id, 'SUB', 'READONLY', 3);

    RAISE NOTICE 'Created Form: Loan Disbursement Form (ID: %)', v_disbursement_form_id;

    -- =============================================================================
    -- 4. Create BPMN Process
    -- =============================================================================
    
    -- Process will be inserted by PowerShell script
    -- See: 02-insert-bpmn-process.ps1
    
    -- =============================================================================
    -- 5. Create Actions (Including Form Popup Actions)
    -- =============================================================================
    
    -- 5.1 Submit Loan Application
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        config_json,
        icon,
        button_color
    ) VALUES (
        v_function_unit_id,
        'Submit Loan Application',
        'PROCESS_SUBMIT',
        'Submit a new loan application for processing',
        '{
            "requireComment": false,
            "confirmMessage": "Submit this loan application?",
            "successMessage": "Loan application submitted successfully"
        }'::jsonb,
        'Upload',
        'primary'
    ) RETURNING id INTO v_submit_action_id;

    -- 5.2 Perform Credit Check (FORM POPUP ACTION)
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        config_json,
        icon,
        button_color
    ) VALUES (
        v_function_unit_id,
        'Perform Credit Check',
        'FORM_POPUP',
        'Open credit check form to record bureau results',
        format('{
            "formId": %s,
            "formName": "Credit Check Form",
            "popupWidth": "800px",
            "popupTitle": "Credit Bureau Check",
            "requireComment": false,
            "allowedRoles": ["CREDIT_OFFICER", "RISK_MANAGER"],
            "successMessage": "Credit check results saved successfully"
        }', v_credit_check_form_id)::jsonb,
        'FileSearch',
        'info'
    ) RETURNING id INTO v_credit_check_action_id;

    -- 5.3 View Credit Report (FORM POPUP ACTION - Read Only)
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        config_json,
        icon,
        button_color
    ) VALUES (
        v_function_unit_id,
        'View Credit Report',
        'FORM_POPUP',
        'View credit check results in popup',
        format('{
            "formId": %s,
            "formName": "Credit Check Form",
            "popupWidth": "800px",
            "popupTitle": "Credit Report",
            "readOnly": true,
            "showSubmitButton": false
        }', v_credit_check_form_id)::jsonb,
        'Document',
        'default'
    ) RETURNING id INTO v_view_credit_action_id;

    -- 5.4 Calculate EMI (API CALL ACTION)
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        config_json,
        icon,
        button_color
    ) VALUES (
        v_function_unit_id,
        'Calculate EMI',
        'API_CALL',
        'Calculate monthly EMI based on loan amount and tenure',
        '{
            "url": "/api/lending/calculate-emi",
            "method": "POST",
            "parameters": {
                "loanAmount": "{{loan_amount}}",
                "tenureMonths": "{{loan_tenure_months}}",
                "interestRate": "{{interest_rate}}"
            },
            "updateFields": {
                "emi_amount": "{{response.emiAmount}}"
            },
            "successMessage": "EMI calculated successfully"
        }'::jsonb,
        'Calculator',
        'info'
    ) RETURNING id INTO v_calculate_emi_action_id;

    -- 5.5 Approve Loan
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        config_json,
        icon,
        button_color
    ) VALUES (
        v_function_unit_id,
        'Approve Loan',
        'APPROVE',
        'Approve the loan application',
        '{
            "targetStatus": "APPROVED",
            "requireComment": true,
            "confirmMessage": "Approve this loan application?",
            "allowedRoles": ["LOAN_MANAGER", "SENIOR_MANAGER"],
            "successMessage": "Loan application approved"
        }'::jsonb,
        'Check',
        'success'
    ) RETURNING id INTO v_approve_action_id;

    -- 5.6 Reject Loan
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        config_json,
        icon,
        button_color
    ) VALUES (
        v_function_unit_id,
        'Reject Loan',
        'REJECT',
        'Reject the loan application',
        '{
            "targetStatus": "REJECTED",
            "requireComment": true,
            "requireReason": true,
            "confirmMessage": "Reject this loan application?",
            "successMessage": "Loan application rejected"
        }'::jsonb,
        'Close',
        'danger'
    ) RETURNING id INTO v_reject_action_id;

    -- 5.7 Request Additional Information (FORM POPUP ACTION)
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        config_json,
        icon,
        button_color
    ) VALUES (
        v_function_unit_id,
        'Request Additional Info',
        'FORM_POPUP',
        'Request additional information from applicant',
        '{
            "formType": "COMMENT_FORM",
            "popupWidth": "600px",
            "popupTitle": "Request Additional Information",
            "requireComment": true,
            "commentLabel": "Please specify what additional information is required",
            "targetStatus": "INFO_REQUIRED",
            "notifyApplicant": true,
            "successMessage": "Information request sent to applicant"
        }'::jsonb,
        'QuestionCircle',
        'warning'
    ) RETURNING id INTO v_request_info_action_id;

    -- 5.8 Assess Risk (FORM POPUP ACTION)
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        config_json,
        icon,
        button_color
    ) VALUES (
        v_function_unit_id,
        'Assess Risk',
        'FORM_POPUP',
        'Perform risk assessment on loan application',
        format('{
            "formId": %s,
            "formName": "Risk Assessment Form",
            "popupWidth": "900px",
            "popupTitle": "Risk Assessment & Evaluation",
            "requireComment": false,
            "allowedRoles": ["RISK_OFFICER", "RISK_MANAGER"],
            "successMessage": "Risk assessment completed"
        }', v_risk_assessment_form_id)::jsonb,
        'Warning',
        'warning'
    );

    -- 5.9 Verify Documents
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        config_json,
        icon,
        button_color
    ) VALUES (
        v_function_unit_id,
        'Verify Documents',
        'APPROVE',
        'Verify all uploaded documents',
        '{
            "targetStatus": "DOCUMENTS_VERIFIED",
            "requireComment": false,
            "allowedRoles": ["DOCUMENT_VERIFIER", "OPERATIONS"],
            "successMessage": "Documents verified successfully"
        }'::jsonb,
        'FileDone',
        'success'
    );

    -- 5.10 Process Disbursement
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        config_json,
        icon,
        button_color
    ) VALUES (
        v_function_unit_id,
        'Process Disbursement',
        'APPROVE',
        'Process loan disbursement to applicant account',
        '{
            "targetStatus": "DISBURSED",
            "requireComment": true,
            "confirmMessage": "Process disbursement for this loan?",
            "allowedRoles": ["FINANCE_OFFICER", "FINANCE_MANAGER"],
            "successMessage": "Loan disbursed successfully"
        }'::jsonb,
        'DollarCircle',
        'success'
    );

    -- 5.11 Withdraw Application
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        config_json,
        icon,
        button_color
    ) VALUES (
        v_function_unit_id,
        'Withdraw Application',
        'WITHDRAW',
        'Withdraw the loan application',
        '{
            "targetStatus": "WITHDRAWN",
            "requireComment": true,
            "requireReason": true,
            "allowedFromStatus": ["SUBMITTED", "IN_REVIEW", "INFO_REQUIRED"],
            "confirmMessage": "Withdraw this loan application?",
            "successMessage": "Loan application withdrawn"
        }'::jsonb,
        'RollbackOutlined',
        'default'
    );

    -- 5.12 Query Loan Applications (API CALL)
    INSERT INTO public.dw_action_definitions (
        function_unit_id,
        action_name,
        action_type,
        description,
        config_json,
        icon,
        button_color
    ) VALUES (
        v_function_unit_id,
        'Query Applications',
        'API_CALL',
        'Query loan applications with filters',
        '{
            "url": "/api/lending/applications",
            "method": "GET",
            "parameters": {
                "status": "{{status}}",
                "loanType": "{{loan_type}}",
                "fromDate": "{{from_date}}",
                "toDate": "{{to_date}}"
            }
        }'::jsonb,
        'Search',
        'info'
    );

    RAISE NOTICE 'Created 12 actions including multiple FORM_POPUP actions';

    -- =============================================================================
    -- 6. Bind Actions to Process Tasks
    -- =============================================================================
    
    -- Note: Action bindings will be created after process is inserted
    -- See: 03-bind-actions.sql

    -- =============================================================================
    -- Summary
    -- =============================================================================
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Digital Lending System Created Successfully!';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Function Unit ID: %', v_function_unit_id;
    RAISE NOTICE '';
    RAISE NOTICE 'Tables Created:';
    RAISE NOTICE '  - Loan Application (Main): %', v_loan_application_table_id;
    RAISE NOTICE '  - Applicant Information (Sub): %', v_applicant_info_table_id;
    RAISE NOTICE '  - Financial Information (Sub): %', v_financial_info_table_id;
    RAISE NOTICE '  - Collateral Details (Sub): %', v_collateral_table_id;
    RAISE NOTICE '  - Credit Check Results (Related): %', v_credit_check_table_id;
    RAISE NOTICE '  - Approval History (Related): %', v_approval_history_table_id;
    RAISE NOTICE '  - Documents (Related): %', v_documents_table_id;
    RAISE NOTICE '';
    RAISE NOTICE 'Forms Created:';
    RAISE NOTICE '  - Loan Application Form: %', v_application_form_id;
    RAISE NOTICE '  - Credit Check Form (POPUP): %', v_credit_check_form_id;
    RAISE NOTICE '  - Risk Assessment Form (POPUP): %', v_risk_assessment_form_id;
    RAISE NOTICE '  - Loan Approval Form: %', v_approval_form_id;
    RAISE NOTICE '  - Loan Disbursement Form: %', v_disbursement_form_id;
    RAISE NOTICE '';
    RAISE NOTICE 'Actions Created: 12 (including FORM_POPUP actions)';
    RAISE NOTICE '';
    RAISE NOTICE 'Next Steps:';
    RAISE NOTICE '  1. Run: 02-insert-bpmn-process.ps1';
    RAISE NOTICE '  2. Run: 03-bind-actions.sql';
    RAISE NOTICE '  3. Deploy the function unit in Developer Workstation';
    RAISE NOTICE '========================================';
END $$;
