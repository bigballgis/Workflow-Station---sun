-- =============================================================================
-- Digital Lending System - Complete Setup
-- Created using the AI Function Unit Generation Framework
--
-- Features:
-- 1. Complete data model (7 tables)
-- 2. Multiple form types (main forms, popup forms)
-- 3. Complex approval workflow (8 nodes)
-- 4. Rich business actions (15 actions)
-- 5. Multiple task assignment methods
-- 6. Form popup operations
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
BEGIN

    -- =========================================================================
    -- Part 1: Create Function Unit
    -- =========================================================================

    INSERT INTO dw_function_units (
        code, name, description, status, version, is_active,
        deployed_at, created_by, created_at, updated_at
    ) VALUES (
        'DIGITAL_LENDING_V2_EN',
        'Digital Lending System V2 (EN)',
        'Full-featured digital loan application and approval system with credit checks, risk assessment, collateral management, multi-level approval, and automated disbursement',
        'DRAFT', '1.0.0', true,
        CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) RETURNING id INTO v_function_unit_id;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'Created Function Unit: Digital Lending System V2';
    RAISE NOTICE 'Function Unit ID: %', v_function_unit_id;
    RAISE NOTICE '========================================';
    -- =========================================================================
    -- Part 2: Create Table Definitions
    -- =========================================================================

    -- 2.1 Main Table: Loan Application
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id, 'Loan Application', 'MAIN',
        'Main loan application table, records core loan information'
    ) RETURNING id INTO v_loan_application_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_loan_application_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_loan_application_table_id, 'application_number', 'VARCHAR', 50, NULL, FALSE, 'Application number (unique)', 2),
    (v_loan_application_table_id, 'application_date', 'TIMESTAMP', NULL, NULL, FALSE, 'Application date', 3),
    (v_loan_application_table_id, 'loan_type', 'VARCHAR', 50, NULL, FALSE, 'Loan type (Personal/Mortgage/Auto/Business)', 4),
    (v_loan_application_table_id, 'loan_amount', 'DECIMAL', 15, 2, FALSE, 'Requested amount', 5),
    (v_loan_application_table_id, 'loan_tenure_months', 'INTEGER', NULL, NULL, FALSE, 'Loan tenure (months)', 6),
    (v_loan_application_table_id, 'interest_rate', 'DECIMAL', 5, 2, TRUE, 'Annual interest rate (%)', 7),
    (v_loan_application_table_id, 'emi_amount', 'DECIMAL', 15, 2, TRUE, 'Monthly installment amount', 8),
    (v_loan_application_table_id, 'loan_purpose', 'TEXT', NULL, NULL, FALSE, 'Loan purpose', 9),
    (v_loan_application_table_id, 'status', 'VARCHAR', 30, NULL, FALSE, 'Application status', 10),
    (v_loan_application_table_id, 'current_stage', 'VARCHAR', 50, NULL, TRUE, 'Current workflow stage', 11),
    (v_loan_application_table_id, 'risk_rating', 'VARCHAR', 20, NULL, TRUE, 'Risk rating (Low/Medium/High)', 12),
    (v_loan_application_table_id, 'credit_score', 'INTEGER', NULL, NULL, TRUE, 'Credit score', 13),
    (v_loan_application_table_id, 'approval_date', 'TIMESTAMP', NULL, NULL, TRUE, 'Approval date', 14),
    (v_loan_application_table_id, 'disbursement_date', 'TIMESTAMP', NULL, NULL, TRUE, 'Disbursement date', 15),
    (v_loan_application_table_id, 'rejection_reason', 'TEXT', NULL, NULL, TRUE, 'Rejection reason', 16),
    (v_loan_application_table_id, 'created_by', 'VARCHAR', 100, NULL, FALSE, 'Created by', 17),
    (v_loan_application_table_id, 'created_at', 'TIMESTAMP', NULL, NULL, FALSE, 'Created at', 18),
    (v_loan_application_table_id, 'updated_at', 'TIMESTAMP', NULL, NULL, TRUE, 'Updated at', 19);

    RAISE NOTICE 'Created main table: Loan Application (ID: %)', v_loan_application_table_id;

    -- 2.2 Sub Table: Applicant Information
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id, 'Applicant Information', 'SUB',
        'Applicant personal information (supports primary and co-applicants)'
    ) RETURNING id INTO v_applicant_info_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_applicant_info_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_applicant_info_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, 'FK: Loan Application ID', 2),
    (v_applicant_info_table_id, 'applicant_type', 'VARCHAR', 20, NULL, FALSE, 'Applicant type (Primary/Co-applicant)', 3),
    (v_applicant_info_table_id, 'full_name', 'VARCHAR', 200, NULL, FALSE, 'Full name', 4),
    (v_applicant_info_table_id, 'date_of_birth', 'DATE', NULL, NULL, FALSE, 'Date of birth', 5),
    (v_applicant_info_table_id, 'gender', 'VARCHAR', 20, NULL, FALSE, 'Gender', 6),
    (v_applicant_info_table_id, 'marital_status', 'VARCHAR', 20, NULL, FALSE, 'Marital status', 7),
    (v_applicant_info_table_id, 'nationality', 'VARCHAR', 50, NULL, FALSE, 'Nationality', 8),
    (v_applicant_info_table_id, 'id_type', 'VARCHAR', 50, NULL, FALSE, 'ID document type', 9),
    (v_applicant_info_table_id, 'id_number', 'VARCHAR', 50, NULL, FALSE, 'ID document number', 10),
    (v_applicant_info_table_id, 'mobile_number', 'VARCHAR', 20, NULL, FALSE, 'Mobile number', 11),
    (v_applicant_info_table_id, 'email', 'VARCHAR', 100, NULL, FALSE, 'Email address', 12),
    (v_applicant_info_table_id, 'current_address', 'TEXT', NULL, NULL, FALSE, 'Current residential address', 13),
    (v_applicant_info_table_id, 'permanent_address', 'TEXT', NULL, NULL, TRUE, 'Permanent address', 14),
    (v_applicant_info_table_id, 'years_at_current_address', 'INTEGER', NULL, NULL, TRUE, 'Years at current address', 15);

    RAISE NOTICE 'Created sub table: Applicant Information (ID: %)', v_applicant_info_table_id;

    -- 2.3 Sub Table: Financial Information
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id, 'Financial Information', 'SUB',
        'Applicant financial status information'
    ) RETURNING id INTO v_financial_info_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_financial_info_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_financial_info_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, 'FK: Loan Application ID', 2),
    (v_financial_info_table_id, 'employment_type', 'VARCHAR', 50, NULL, FALSE, 'Employment type (Salaried/Self-employed/Business owner)', 3),
    (v_financial_info_table_id, 'employer_name', 'VARCHAR', 200, NULL, TRUE, 'Employer/Company name', 4),
    (v_financial_info_table_id, 'occupation', 'VARCHAR', 100, NULL, FALSE, 'Occupation/Position', 5),
    (v_financial_info_table_id, 'years_of_employment', 'INTEGER', NULL, NULL, TRUE, 'Years of employment', 6),
    (v_financial_info_table_id, 'monthly_income', 'DECIMAL', 15, 2, FALSE, 'Monthly income', 7),
    (v_financial_info_table_id, 'other_income', 'DECIMAL', 15, 2, TRUE, 'Other income', 8),
    (v_financial_info_table_id, 'monthly_expenses', 'DECIMAL', 15, 2, FALSE, 'Monthly expenses', 9),
    (v_financial_info_table_id, 'existing_loans', 'DECIMAL', 15, 2, TRUE, 'Total existing loans', 10),
    (v_financial_info_table_id, 'existing_emi', 'DECIMAL', 15, 2, TRUE, 'Total existing EMI', 11),
    (v_financial_info_table_id, 'bank_name', 'VARCHAR', 100, NULL, FALSE, 'Primary bank name', 12),
    (v_financial_info_table_id, 'account_number', 'VARCHAR', 50, NULL, FALSE, 'Bank account number', 13),
    (v_financial_info_table_id, 'account_type', 'VARCHAR', 30, NULL, FALSE, 'Account type (Savings/Current)', 14);

    RAISE NOTICE 'Created sub table: Financial Information (ID: %)', v_financial_info_table_id;

    -- 2.4 Sub Table: Collateral Details
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id, 'Collateral Details', 'SUB',
        'Collateral/security details (for secured loans)'
    ) RETURNING id INTO v_collateral_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_collateral_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_collateral_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, 'FK: Loan Application ID', 2),
    (v_collateral_table_id, 'collateral_type', 'VARCHAR', 50, NULL, FALSE, 'Collateral type (Property/Vehicle/Securities/Fixed Deposit)', 3),
    (v_collateral_table_id, 'collateral_description', 'TEXT', NULL, NULL, FALSE, 'Collateral detailed description', 4),
    (v_collateral_table_id, 'estimated_value', 'DECIMAL', 15, 2, FALSE, 'Estimated value', 5),
    (v_collateral_table_id, 'valuation_date', 'DATE', NULL, NULL, TRUE, 'Valuation date', 6),
    (v_collateral_table_id, 'valuer_name', 'VARCHAR', 100, NULL, TRUE, 'Valuer name', 7),
    (v_collateral_table_id, 'ownership_proof', 'VARCHAR', 200, NULL, TRUE, 'Ownership proof document', 8),
    (v_collateral_table_id, 'encumbrance_status', 'VARCHAR', 50, NULL, TRUE, 'Encumbrance status', 9);

    RAISE NOTICE 'Created sub table: Collateral Details (ID: %)', v_collateral_table_id;
    -- 2.5 Relation Table: Credit Check Results
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id, 'Credit Check Results', 'RELATION',
        'Credit bureau check result records'
    ) RETURNING id INTO v_credit_check_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_credit_check_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_credit_check_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, 'FK: Loan Application ID', 2),
    (v_credit_check_table_id, 'bureau_name', 'VARCHAR', 100, NULL, FALSE, 'Credit bureau name', 3),
    (v_credit_check_table_id, 'check_date', 'TIMESTAMP', NULL, NULL, FALSE, 'Check date', 4),
    (v_credit_check_table_id, 'credit_score', 'INTEGER', NULL, NULL, FALSE, 'Credit score', 5),
    (v_credit_check_table_id, 'score_range', 'VARCHAR', 50, NULL, TRUE, 'Score range (e.g. 300-900)', 6),
    (v_credit_check_table_id, 'credit_history_length', 'INTEGER', NULL, NULL, TRUE, 'Credit history length (months)', 7),
    (v_credit_check_table_id, 'total_accounts', 'INTEGER', NULL, NULL, TRUE, 'Total accounts', 8),
    (v_credit_check_table_id, 'active_accounts', 'INTEGER', NULL, NULL, TRUE, 'Active accounts', 9),
    (v_credit_check_table_id, 'delinquent_accounts', 'INTEGER', NULL, NULL, TRUE, 'Delinquent accounts', 10),
    (v_credit_check_table_id, 'total_debt', 'DECIMAL', 15, 2, TRUE, 'Total debt', 11),
    (v_credit_check_table_id, 'credit_utilization', 'DECIMAL', 5, 2, TRUE, 'Credit utilization (%)', 12),
    (v_credit_check_table_id, 'payment_history', 'VARCHAR', 20, NULL, TRUE, 'Payment history rating', 13),
    (v_credit_check_table_id, 'remarks', 'TEXT', NULL, NULL, TRUE, 'Remarks', 14);

    RAISE NOTICE 'Created relation table: Credit Check Results (ID: %)', v_credit_check_table_id;

    -- 2.6 Relation Table: Approval History
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id, 'Approval History', 'RELATION',
        'Historical records of all approval operations'
    ) RETURNING id INTO v_approval_history_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_approval_history_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_approval_history_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, 'FK: Loan Application ID', 2),
    (v_approval_history_table_id, 'stage_name', 'VARCHAR', 100, NULL, FALSE, 'Approval stage name', 3),
    (v_approval_history_table_id, 'approver_name', 'VARCHAR', 100, NULL, FALSE, 'Approver name', 4),
    (v_approval_history_table_id, 'approver_role', 'VARCHAR', 50, NULL, FALSE, 'Approver role', 5),
    (v_approval_history_table_id, 'action', 'VARCHAR', 30, NULL, FALSE, 'Action type', 6),
    (v_approval_history_table_id, 'decision', 'VARCHAR', 20, NULL, FALSE, 'Decision (Approve/Reject/Return)', 7),
    (v_approval_history_table_id, 'action_date', 'TIMESTAMP', NULL, NULL, FALSE, 'Action date', 8),
    (v_approval_history_table_id, 'comments', 'TEXT', NULL, NULL, TRUE, 'Approval comments', 9),
    (v_approval_history_table_id, 'conditions', 'TEXT', NULL, NULL, TRUE, 'Approval conditions', 10);

    RAISE NOTICE 'Created relation table: Approval History (ID: %)', v_approval_history_table_id;

    -- 2.7 Relation Table: Documents
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id, 'Documents', 'RELATION',
        'Supporting documents and attachments for loan applications'
    ) RETURNING id INTO v_documents_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_documents_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, 'Primary key', 1),
    (v_documents_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, 'FK: Loan Application ID', 2),
    (v_documents_table_id, 'document_type', 'VARCHAR', 100, NULL, FALSE, 'Document type', 3),
    (v_documents_table_id, 'document_name', 'VARCHAR', 200, NULL, FALSE, 'Document name', 4),
    (v_documents_table_id, 'file_path', 'VARCHAR', 500, NULL, FALSE, 'File storage path', 5),
    (v_documents_table_id, 'file_size', 'BIGINT', NULL, NULL, TRUE, 'File size (bytes)', 6),
    (v_documents_table_id, 'upload_date', 'TIMESTAMP', NULL, NULL, FALSE, 'Upload date', 7),
    (v_documents_table_id, 'uploaded_by', 'VARCHAR', 100, NULL, FALSE, 'Uploaded by', 8),
    (v_documents_table_id, 'verification_status', 'VARCHAR', 30, NULL, TRUE, 'Verification status', 9),
    (v_documents_table_id, 'verified_by', 'VARCHAR', 100, NULL, TRUE, 'Verified by', 10),
    (v_documents_table_id, 'verification_date', 'TIMESTAMP', NULL, NULL, TRUE, 'Verification date', 11);

    RAISE NOTICE 'Created relation table: Documents (ID: %)', v_documents_table_id;
    -- =========================================================================
    -- Part 3: Create Form Definitions
    -- =========================================================================

    -- 3.1 Loan Application Form (Main Form)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, config_json, description, bound_table_id
    ) VALUES (
        v_function_unit_id, 'Loan Application Form', 'MAIN',
        '{"layout":"vertical","labelWidth":"150px","size":"default","showSubmitButton":true,"submitButtonText":"Submit Application"}'::jsonb,
        'Complete loan application form for customers to fill out',
        v_loan_application_table_id
    ) RETURNING id INTO v_application_form_id;

    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, sort_order) VALUES
    (v_application_form_id, v_loan_application_table_id, 'PRIMARY', 'EDITABLE', 1),
    (v_application_form_id, v_applicant_info_table_id, 'SUB', 'EDITABLE', 2),
    (v_application_form_id, v_financial_info_table_id, 'SUB', 'EDITABLE', 3),
    (v_application_form_id, v_collateral_table_id, 'SUB', 'EDITABLE', 4),
    (v_application_form_id, v_documents_table_id, 'RELATED', 'EDITABLE', 5);
    RAISE NOTICE 'Created form: Loan Application Form (ID: %)', v_application_form_id;

    -- 3.2 Credit Check Form (Popup)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, config_json, description, bound_table_id
    ) VALUES (
        v_function_unit_id, 'Credit Check Form', 'POPUP',
        '{"layout":"vertical","labelWidth":"150px","size":"default","width":"800px","title":"Credit Bureau Check"}'::jsonb,
        'Popup form for credit officers to record credit check results',
        v_credit_check_table_id
    ) RETURNING id INTO v_credit_check_form_id;

    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, sort_order) VALUES
    (v_credit_check_form_id, v_loan_application_table_id, 'PRIMARY', 'READONLY', 1),
    (v_credit_check_form_id, v_credit_check_table_id, 'RELATED', 'EDITABLE', 2);
    RAISE NOTICE 'Created form: Credit Check Form (ID: %)', v_credit_check_form_id;

    -- 3.3 Risk Assessment Form (Popup)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, config_json, description, bound_table_id
    ) VALUES (
        v_function_unit_id, 'Risk Assessment Form', 'POPUP',
        '{"layout":"vertical","labelWidth":"150px","size":"default","width":"900px","title":"Risk Assessment & Analysis"}'::jsonb,
        'Popup form for risk officers to assess loan risk',
        v_loan_application_table_id
    ) RETURNING id INTO v_risk_assessment_form_id;

    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, sort_order) VALUES
    (v_risk_assessment_form_id, v_loan_application_table_id, 'PRIMARY', 'EDITABLE', 1),
    (v_risk_assessment_form_id, v_applicant_info_table_id, 'SUB', 'READONLY', 2),
    (v_risk_assessment_form_id, v_financial_info_table_id, 'SUB', 'READONLY', 3),
    (v_risk_assessment_form_id, v_credit_check_table_id, 'RELATED', 'READONLY', 4);
    RAISE NOTICE 'Created form: Risk Assessment Form (ID: %)', v_risk_assessment_form_id;

    -- 3.4 Loan Approval Form (Main)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, config_json, description, bound_table_id
    ) VALUES (
        v_function_unit_id, 'Loan Approval Form', 'MAIN',
        '{"layout":"vertical","labelWidth":"150px","size":"default","readonly":false}'::jsonb,
        'Form for managers to approve loan applications',
        v_loan_application_table_id
    ) RETURNING id INTO v_approval_form_id;

    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, sort_order) VALUES
    (v_approval_form_id, v_loan_application_table_id, 'PRIMARY', 'READONLY', 1),
    (v_approval_form_id, v_applicant_info_table_id, 'SUB', 'READONLY', 2),
    (v_approval_form_id, v_financial_info_table_id, 'SUB', 'READONLY', 3),
    (v_approval_form_id, v_collateral_table_id, 'SUB', 'READONLY', 4),
    (v_approval_form_id, v_credit_check_table_id, 'RELATED', 'READONLY', 5),
    (v_approval_form_id, v_approval_history_table_id, 'RELATED', 'EDITABLE', 6);
    RAISE NOTICE 'Created form: Loan Approval Form (ID: %)', v_approval_form_id;

    -- 3.5 Loan Disbursement Form (Main)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, config_json, description, bound_table_id
    ) VALUES (
        v_function_unit_id, 'Loan Disbursement Form', 'MAIN',
        '{"layout":"vertical","labelWidth":"150px","size":"default"}'::jsonb,
        'Form for finance team to process loan disbursement',
        v_loan_application_table_id
    ) RETURNING id INTO v_disbursement_form_id;

    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, sort_order) VALUES
    (v_disbursement_form_id, v_loan_application_table_id, 'PRIMARY', 'EDITABLE', 1),
    (v_disbursement_form_id, v_applicant_info_table_id, 'SUB', 'READONLY', 2),
    (v_disbursement_form_id, v_financial_info_table_id, 'SUB', 'READONLY', 3);
    RAISE NOTICE 'Created form: Loan Disbursement Form (ID: %)', v_disbursement_form_id;
    -- =========================================================================
    -- Part 4: Create Action Definitions
    -- =========================================================================

    -- 4.1 Submit Application
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Submit Application', 'PROCESS_SUBMIT', 'Submit loan application to start the approval workflow',
     '{"requireComment":false,"confirmMessage":"Confirm submitting this loan application?","successMessage":"Loan application submitted successfully"}'::jsonb, 'Upload', 'primary');

    -- 4.2 Withdraw Application
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Withdraw Application', 'WITHDRAW', 'Withdraw a submitted loan application',
     '{"targetStatus":"WITHDRAWN","requireComment":true,"requireReason":true,"allowedFromStatus":["SUBMITTED","IN_REVIEW","INFO_REQUIRED"],"confirmMessage":"Confirm withdrawing this loan application?","successMessage":"Loan application has been withdrawn"}'::jsonb, 'RollbackOutlined', 'warning');

    -- 4.3 Perform Credit Check (Popup)
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Perform Credit Check', 'FORM_POPUP', 'Open credit check form to record credit bureau results',
     format('{"formId":%s,"formName":"Credit Check Form","popupWidth":"800px","popupTitle":"Credit Bureau Check","requireComment":false,"allowedRoles":["CREDIT_OFFICER","RISK_MANAGER"],"successMessage":"Credit check results saved"}', v_credit_check_form_id)::jsonb, 'FileSearch', 'info');

    -- 4.4 View Credit Report (Popup, Read-only)
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'View Credit Report', 'FORM_POPUP', 'View credit check results (read-only)',
     format('{"formId":%s,"formName":"Credit Check Form","popupWidth":"800px","popupTitle":"Credit Report","readOnly":true,"showSubmitButton":false}', v_credit_check_form_id)::jsonb, 'Document', 'default');

    -- 4.5 Assess Risk (Popup)
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Assess Risk', 'FORM_POPUP', 'Perform risk assessment on the loan application',
     format('{"formId":%s,"formName":"Risk Assessment Form","popupWidth":"900px","popupTitle":"Risk Assessment & Analysis","requireComment":false,"allowedRoles":["RISK_OFFICER","RISK_MANAGER"],"successMessage":"Risk assessment completed"}', v_risk_assessment_form_id)::jsonb, 'Warning', 'warning');

    -- 4.6 Approve
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Approve', 'APPROVE', 'Approve the loan application',
     '{"targetStatus":"APPROVED","requireComment":true,"confirmMessage":"Confirm approving this loan application?","allowedRoles":["MANAGER","SENIOR_MANAGER"],"successMessage":"Loan application approved"}'::jsonb, 'Check', 'success');

    -- 4.7 Reject
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Reject', 'REJECT', 'Reject the loan application',
     '{"targetStatus":"REJECTED","requireComment":true,"requireReason":true,"confirmMessage":"Confirm rejecting this loan application?","successMessage":"Loan application rejected"}'::jsonb, 'Close', 'danger');

    -- 4.8 Request Additional Info (Popup)
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Request Additional Info', 'FORM_POPUP', 'Request additional information from the applicant',
     '{"formType":"COMMENT_FORM","popupWidth":"600px","popupTitle":"Request Additional Information","requireComment":true,"commentLabel":"Please specify the information needed","targetStatus":"INFO_REQUIRED","notifyApplicant":true,"successMessage":"Information request sent to applicant"}'::jsonb, 'QuestionCircle', 'warning');

    -- 4.9 Verify Documents
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Verify Documents', 'APPROVE', 'Verify all uploaded documents',
     '{"targetStatus":"DOCUMENTS_VERIFIED","requireComment":false,"allowedRoles":["DOCUMENT_VERIFIER","OPERATIONS"],"successMessage":"Documents verified successfully"}'::jsonb, 'FileDone', 'success');

    -- 4.10 Calculate EMI (API Call)
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Calculate EMI', 'API_CALL', 'Calculate monthly installment based on loan amount and tenure',
     '{"url":"/api/lending/calculate-emi","method":"POST","parameters":{"loanAmount":"{{loan_amount}}","tenureMonths":"{{loan_tenure_months}}","interestRate":"{{interest_rate}}"},"updateFields":{"emi_amount":"{{response.emiAmount}}"},"successMessage":"EMI calculated successfully"}'::jsonb, 'Calculator', 'info');

    -- 4.11 Process Disbursement
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Process Disbursement', 'APPROVE', 'Process loan disbursement to applicant account',
     '{"targetStatus":"DISBURSED","requireComment":true,"confirmMessage":"Confirm processing disbursement for this loan?","allowedRoles":["FINANCE_OFFICER","FINANCE_MANAGER"],"successMessage":"Loan disbursed successfully"}'::jsonb, 'DollarCircle', 'success');

    -- 4.12 Query Applications (API Call)
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Query Applications', 'API_CALL', 'Query loan applications with filter criteria',
     '{"url":"/api/lending/applications","method":"GET","parameters":{"status":"{{status}}","loanType":"{{loan_type}}","fromDate":"{{from_date}}","toDate":"{{to_date}}"}}'::jsonb, 'Search', 'info');

    -- 4.13 Verify Account (API Call)
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Verify Account', 'API_CALL', 'Verify applicant bank account',
     '{"url":"/api/lending/verify-account","method":"POST","parameters":{"bankName":"{{bank_name}}","accountNumber":"{{account_number}}","accountType":"{{account_type}}"},"successMessage":"Account verified successfully"}'::jsonb, 'BankOutlined', 'info');

    -- 4.14 Mark as Low Risk
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Mark as Low Risk', 'APPROVE', 'Mark the loan as low risk',
     '{"targetStatus":"LOW_RISK","requireComment":false,"updateFields":{"risk_rating":"Low"},"successMessage":"Marked as low risk"}'::jsonb, 'CheckCircle', 'success');

    -- 4.15 Mark as High Risk
    INSERT INTO dw_action_definitions (function_unit_id, action_name, action_type, description, config_json, icon, button_color) VALUES
    (v_function_unit_id, 'Mark as High Risk', 'REJECT', 'Mark the loan as high risk',
     '{"targetStatus":"HIGH_RISK","requireComment":true,"requireReason":true,"updateFields":{"risk_rating":"High"},"successMessage":"Marked as high risk"}'::jsonb, 'WarningOutlined', 'danger');

    RAISE NOTICE 'Created 15 action definitions';
    -- =========================================================================
    -- Part 5: Output Summary
    -- =========================================================================

    RAISE NOTICE '';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Digital Lending System V2 created successfully!';
    RAISE NOTICE '========================================';
    RAISE NOTICE '';
    RAISE NOTICE 'Function Unit ID: %', v_function_unit_id;
    RAISE NOTICE '';
    RAISE NOTICE 'Table Definitions:';
    RAISE NOTICE '  - Loan Application (main): %', v_loan_application_table_id;
    RAISE NOTICE '  - Applicant Information (sub): %', v_applicant_info_table_id;
    RAISE NOTICE '  - Financial Information (sub): %', v_financial_info_table_id;
    RAISE NOTICE '  - Collateral Details (sub): %', v_collateral_table_id;
    RAISE NOTICE '  - Credit Check Results (relation): %', v_credit_check_table_id;
    RAISE NOTICE '  - Approval History (relation): %', v_approval_history_table_id;
    RAISE NOTICE '  - Documents (relation): %', v_documents_table_id;
    RAISE NOTICE '';
    RAISE NOTICE 'Form Definitions:';
    RAISE NOTICE '  - Loan Application Form: %', v_application_form_id;
    RAISE NOTICE '  - Credit Check Form (popup): %', v_credit_check_form_id;
    RAISE NOTICE '  - Risk Assessment Form (popup): %', v_risk_assessment_form_id;
    RAISE NOTICE '  - Loan Approval Form: %', v_approval_form_id;
    RAISE NOTICE '  - Loan Disbursement Form: %', v_disbursement_form_id;
    RAISE NOTICE '';
    RAISE NOTICE 'Action Definitions: 15 (including popup form actions and API calls)';
    RAISE NOTICE '';
    RAISE NOTICE 'Next Steps:';
    RAISE NOTICE '  1. Run: 02-insert-bpmn-process.sql';
    RAISE NOTICE '  2. Run: 03-bind-actions.sql';
    RAISE NOTICE '  3. Deploy function unit in Developer Workstation';
    RAISE NOTICE '  4. Test complete workflow in User Portal';
    RAISE NOTICE '========================================';

END $$;