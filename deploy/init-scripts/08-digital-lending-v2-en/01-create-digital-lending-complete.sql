-- =============================================================================
-- Digital Lending System - Complete Setup (EN)
--
-- Conventions (current platform):
-- - Single PROCESS form per function unit: Loan Application (multi-tab el-tabs + subTable).
--   Sub designer tabs load from config_json.subForms[bindingId] — applied in 04-merge-loan-application-subforms.sql.
-- - PROCESS/TASK bindings: PRIMARY + SUB only (no RELATED on process/task forms).
-- - Underwriting steps share one TASK form + dw_form_stage_bindings (BPMN taskDefinitionKey).
-- - Disbursement uses a separate TASK form. ACTION popups may still use RELATED/SUB.
-- - Sample DMN (dw_decision_definitions) + dw_table_relations for capability demo.
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
    v_bind_applicant BIGINT;
    v_bind_financial BIGINT;
    v_bind_collateral BIGINT;
    v_bind_documents BIGINT;
    v_bind_credit BIGINT;
    v_bind_approval_hist BIGINT;
    v_proc_form_cfg TEXT;
BEGIN

    -- =========================================================================
    -- Part 1: Create Function Unit
    -- =========================================================================

    INSERT INTO dw_function_units (
        code, name, description, status, version, is_active,
        deployed_at, created_by, created_at, updated_at
    ) VALUES (
        'fu-20260403-a1b2c6',
        'Digital Lending System V2 (EN)',
        'Full-featured digital loan application and approval system with credit checks, risk assessment, collateral management, multi-level approval, and automated disbursement',
        'DRAFT', '1.0.0', true,
        CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (code) DO UPDATE SET
        name = EXCLUDED.name,
        description = EXCLUDED.description,
        status = EXCLUDED.status,
        version = EXCLUDED.version,
        is_active = EXCLUDED.is_active,
        deployed_at = EXCLUDED.deployed_at,
        updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO v_function_unit_id;

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
    -- 2.5 Sub Table: Credit Check Results (SUB for PROCESS/TASK bindings; no RELATED on process forms)
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id, 'Credit Check Results', 'SUB',
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

    RAISE NOTICE 'Created sub table: Credit Check Results (ID: %)', v_credit_check_table_id;

    -- 2.6 Sub Table: Approval History
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id, 'Approval History', 'SUB',
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

    RAISE NOTICE 'Created sub table: Approval History (ID: %)', v_approval_history_table_id;

    -- 2.7 Sub Table: Documents
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id, 'Documents', 'SUB',
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

    RAISE NOTICE 'Created sub table: Documents (ID: %)', v_documents_table_id;
    -- =========================================================================
    -- Part 3: Create Form Definitions
    -- =========================================================================

    -- 3.1 Loan Application Form (PROCESS, multi-tab el-tabs + subTable)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, config_json, description, bound_table_id
    ) VALUES (
        v_function_unit_id, 'Loan Application Form', 'PROCESS',
        '{"rule":[],"options":{"form":{"labelWidth":"240px","size":"default","labelPosition":"right"},"submitBtn":{"show":true,"innerText":"Submit application"}}}'::jsonb,
        'Complete loan application form (multi-tab PROCESS: primary + SUB tables only)',
        v_loan_application_table_id
    ) RETURNING id INTO v_application_form_id;

    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order) VALUES
    (v_application_form_id, v_loan_application_table_id, 'PRIMARY', 'EDITABLE', NULL, 1),
    (v_application_form_id, v_applicant_info_table_id, 'SUB', 'EDITABLE', 'loan_application_id', 2),
    (v_application_form_id, v_financial_info_table_id, 'SUB', 'EDITABLE', 'loan_application_id', 3),
    (v_application_form_id, v_collateral_table_id, 'SUB', 'EDITABLE', 'loan_application_id', 4),
    (v_application_form_id, v_documents_table_id, 'SUB', 'EDITABLE', 'loan_application_id', 5),
    (v_application_form_id, v_credit_check_table_id, 'SUB', 'EDITABLE', 'loan_application_id', 6),
    (v_application_form_id, v_approval_history_table_id, 'SUB', 'EDITABLE', 'loan_application_id', 7);

    SELECT id INTO v_bind_applicant FROM dw_form_table_bindings WHERE form_id = v_application_form_id AND table_id = v_applicant_info_table_id;
    SELECT id INTO v_bind_financial FROM dw_form_table_bindings WHERE form_id = v_application_form_id AND table_id = v_financial_info_table_id;
    SELECT id INTO v_bind_collateral FROM dw_form_table_bindings WHERE form_id = v_application_form_id AND table_id = v_collateral_table_id;
    SELECT id INTO v_bind_documents FROM dw_form_table_bindings WHERE form_id = v_application_form_id AND table_id = v_documents_table_id;
    SELECT id INTO v_bind_credit FROM dw_form_table_bindings WHERE form_id = v_application_form_id AND table_id = v_credit_check_table_id;
    SELECT id INTO v_bind_approval_hist FROM dw_form_table_bindings WHERE form_id = v_application_form_id AND table_id = v_approval_history_table_id;

    v_proc_form_cfg := $dlProcCfg${"rule":[{"type":"el-tabs","props":{"type":"border-card"},"children":[{"type":"el-tab-pane","props":{"label":"Application","name":"tab_application"},"children":[{"type":"h4","children":["Loan application"],"native":true},{"type":"input","field":"application_number","title":"Application number","props":{"placeholder":"Enter application number","maxlength":50,"showWordLimit":true},"validate":[{"required":true,"message":"Application number is required","trigger":"blur"}]},{"type":"datePicker","field":"application_date","title":"Application date","props":{"type":"datetime","placeholder":"Select application date","valueFormat":"YYYY-MM-DD HH:mm:ss"},"validate":[{"required":true,"message":"Application date is required","trigger":"blur"}]},{"type":"select","field":"loan_type","title":"Loan type","props":{"placeholder":"Select loan type"},"options":[{"value":"Personal","label":"Personal"},{"value":"Mortgage","label":"Mortgage"},{"value":"Auto","label":"Auto"},{"value":"Business","label":"Business"}],"validate":[{"required":true,"message":"Loan type is required","trigger":"change"}]},{"type":"inputNumber","field":"loan_amount","title":"Requested amount","props":{"placeholder":"Enter loan amount","precision":2,"min":0},"validate":[{"required":true,"message":"Loan amount is required","trigger":"blur"}]},{"type":"inputNumber","field":"loan_tenure_months","title":"Tenure (months)","props":{"placeholder":"Months","precision":0,"min":1},"validate":[{"required":true,"message":"Tenure is required","trigger":"blur"}]},{"type":"inputNumber","field":"interest_rate","title":"Annual interest (%)","props":{"placeholder":"Rate","precision":2,"min":0,"max":100}},{"type":"inputNumber","field":"emi_amount","title":"Monthly EMI","props":{"placeholder":"Calculated","precision":2,"disabled":true}},{"type":"input","field":"loan_purpose","title":"Loan purpose","props":{"type":"textarea","placeholder":"Describe purpose","rows":3},"validate":[{"required":true,"message":"Purpose is required","trigger":"blur"}]},{"type":"h4","children":["Status (after submit)"],"native":true},{"type":"input","field":"status","title":"Status","props":{"disabled":true}},{"type":"input","field":"current_stage","title":"Current stage","props":{"disabled":true}},{"type":"input","field":"risk_rating","title":"Risk rating","props":{"disabled":true}},{"type":"inputNumber","field":"credit_score","title":"Credit score","props":{"disabled":true,"precision":0}}]},{"type":"el-tab-pane","props":{"label":"Parties & finance","name":"tab_parties"},"children":[{"type":"h4","children":["Applicants"],"native":true},{"type":"subTable","_bindingId":BIND_APPLICANT},{"type":"h4","children":["Financial profile"],"native":true},{"type":"subTable","_bindingId":BIND_FINANCIAL}]},{"type":"el-tab-pane","props":{"label":"Collateral & documents","name":"tab_security"},"children":[{"type":"h4","children":["Collateral"],"native":true},{"type":"subTable","_bindingId":BIND_COLLATERAL},{"type":"h4","children":["Supporting documents"],"native":true},{"type":"subTable","_bindingId":BIND_DOCUMENTS}]},{"type":"el-tab-pane","props":{"label":"Credit & decisions","name":"tab_credit"},"children":[{"type":"h4","children":["Credit bureau results"],"native":true},{"type":"subTable","_bindingId":BIND_CREDIT},{"type":"h4","children":["Approval trail"],"native":true},{"type":"subTable","_bindingId":BIND_APPROVAL_HIST}]}]}],"options":{"form":{"labelWidth":"240px","size":"default","labelPosition":"right"},"submitBtn":{"show":true,"innerText":"Submit application"}}}$dlProcCfg$;
    v_proc_form_cfg := replace(v_proc_form_cfg, 'BIND_APPLICANT', v_bind_applicant::text);
    v_proc_form_cfg := replace(v_proc_form_cfg, 'BIND_FINANCIAL', v_bind_financial::text);
    v_proc_form_cfg := replace(v_proc_form_cfg, 'BIND_COLLATERAL', v_bind_collateral::text);
    v_proc_form_cfg := replace(v_proc_form_cfg, 'BIND_DOCUMENTS', v_bind_documents::text);
    v_proc_form_cfg := replace(v_proc_form_cfg, 'BIND_CREDIT', v_bind_credit::text);
    v_proc_form_cfg := replace(v_proc_form_cfg, 'BIND_APPROVAL_HIST', v_bind_approval_hist::text);

    UPDATE dw_form_definitions SET config_json = v_proc_form_cfg::jsonb WHERE id = v_application_form_id;

    RAISE NOTICE 'Created form: Loan Application Form (ID: %)', v_application_form_id;

    -- 3.2 Credit Check Form (Popup)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, config_json, description, bound_table_id
    ) VALUES (
        v_function_unit_id, 'Credit Check Form', 'ACTION',
        '{"rule":[{"type":"h4","children":["Loan Application (Read-only)"],"native":true},{"type":"input","field":"application_number_ro","title":"Application Number","props":{"disabled":true,"placeholder":"Application number"}},{"type":"input","field":"loan_type_ro","title":"Loan Type","props":{"disabled":true}},{"type":"inputNumber","field":"loan_amount_ro","title":"Loan Amount","props":{"disabled":true,"precision":2}},{"type":"h4","children":["Credit Check Results"],"native":true},{"type":"input","field":"bureau_name","title":"Credit Bureau","props":{"placeholder":"Enter credit bureau name","maxlength":100},"validate":[{"required":true,"message":"Bureau name is required","trigger":"blur"}]},{"type":"datePicker","field":"check_date","title":"Check Date","props":{"type":"datetime","placeholder":"Select check date","valueFormat":"YYYY-MM-DD HH:mm:ss"},"validate":[{"required":true,"message":"Check date is required","trigger":"blur"}]},{"type":"inputNumber","field":"credit_score","title":"Credit Score","props":{"placeholder":"Enter credit score","precision":0,"min":0,"max":999},"validate":[{"required":true,"message":"Credit score is required","trigger":"blur"}]},{"type":"input","field":"score_range","title":"Score Range","props":{"placeholder":"e.g. 300-900","maxlength":50}},{"type":"inputNumber","field":"credit_history_length","title":"Credit History (months)","props":{"placeholder":"Enter months","precision":0,"min":0}},{"type":"inputNumber","field":"total_accounts","title":"Total Accounts","props":{"placeholder":"Enter total accounts","precision":0,"min":0}},{"type":"inputNumber","field":"active_accounts","title":"Active Accounts","props":{"placeholder":"Enter active accounts","precision":0,"min":0}},{"type":"inputNumber","field":"delinquent_accounts","title":"Delinquent Accounts","props":{"placeholder":"Enter delinquent accounts","precision":0,"min":0}},{"type":"inputNumber","field":"total_debt","title":"Total Debt","props":{"placeholder":"Enter total debt","precision":2,"min":0}},{"type":"inputNumber","field":"credit_utilization","title":"Credit Utilization (%)","props":{"placeholder":"Enter utilization","precision":2,"min":0,"max":100}},{"type":"select","field":"payment_history","title":"Payment History","props":{"placeholder":"Select rating"},"options":[{"value":"Excellent","label":"Excellent"},{"value":"Good","label":"Good"},{"value":"Fair","label":"Fair"},{"value":"Poor","label":"Poor"}]},{"type":"input","field":"remarks","title":"Remarks","props":{"type":"textarea","placeholder":"Enter remarks","rows":3}}],"options":{"form":{"labelWidth":"250px","size":"default","labelPosition":"right"},"submitBtn":{"show":true,"innerText":"Save Results"}}}'::jsonb,
        'Popup form for credit officers to record credit check results',
        v_credit_check_table_id
    ) RETURNING id INTO v_credit_check_form_id;

    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order) VALUES
    (v_credit_check_form_id, v_loan_application_table_id, 'PRIMARY', 'READONLY', NULL, 1),
    (v_credit_check_form_id, v_credit_check_table_id, 'RELATED', 'EDITABLE', 'loan_application_id', 2);
    RAISE NOTICE 'Created form: Credit Check Form (ID: %)', v_credit_check_form_id;

    -- 3.3 Risk Assessment Form (Popup)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, config_json, description, bound_table_id
    ) VALUES (
        v_function_unit_id, 'Risk Assessment Form', 'ACTION',
        '{"rule":[{"type":"h4","children":["Loan Application"],"native":true},{"type":"input","field":"application_number","title":"Application Number","props":{"placeholder":"Application number","maxlength":50}},{"type":"select","field":"loan_type","title":"Loan Type","props":{"placeholder":"Loan type"},"options":[{"value":"Personal","label":"Personal"},{"value":"Mortgage","label":"Mortgage"},{"value":"Auto","label":"Auto"},{"value":"Business","label":"Business"}]},{"type":"inputNumber","field":"loan_amount","title":"Loan Amount","props":{"precision":2}},{"type":"inputNumber","field":"loan_tenure_months","title":"Tenure (months)","props":{"precision":0}},{"type":"h4","children":["Risk Assessment"],"native":true},{"type":"select","field":"risk_rating","title":"Risk Rating","props":{"placeholder":"Select risk rating"},"options":[{"value":"Low","label":"Low"},{"value":"Medium","label":"Medium"},{"value":"High","label":"High"}],"validate":[{"required":true,"message":"Risk rating is required","trigger":"change"}]},{"type":"inputNumber","field":"credit_score","title":"Credit Score","props":{"placeholder":"Enter credit score","precision":0,"min":0,"max":999}},{"type":"h4","children":["Applicant Info (Read-only)"],"native":true},{"type":"input","field":"full_name_ro","title":"Full Name","props":{"disabled":true}},{"type":"input","field":"employment_type_ro","title":"Employment Type","props":{"disabled":true}},{"type":"inputNumber","field":"monthly_income_ro","title":"Monthly Income","props":{"disabled":true,"precision":2}},{"type":"inputNumber","field":"existing_loans_ro","title":"Existing Loans","props":{"disabled":true,"precision":2}},{"type":"h4","children":["Credit Check (Read-only)"],"native":true},{"type":"input","field":"bureau_name_ro","title":"Credit Bureau","props":{"disabled":true}},{"type":"inputNumber","field":"credit_score_ro","title":"Bureau Score","props":{"disabled":true,"precision":0}},{"type":"input","field":"payment_history_ro","title":"Payment History","props":{"disabled":true}}],"options":{"form":{"labelWidth":"250px","size":"default","labelPosition":"right"},"submitBtn":{"show":true,"innerText":"Submit Assessment"}}}'::jsonb,
        'Popup form for risk officers to assess loan risk',
        v_loan_application_table_id
    ) RETURNING id INTO v_risk_assessment_form_id;

    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order) VALUES
    (v_risk_assessment_form_id, v_loan_application_table_id, 'PRIMARY', 'EDITABLE', NULL, 1),
    (v_risk_assessment_form_id, v_applicant_info_table_id, 'SUB', 'READONLY', 'loan_application_id', 2),
    (v_risk_assessment_form_id, v_financial_info_table_id, 'SUB', 'READONLY', 'loan_application_id', 3),
    (v_risk_assessment_form_id, v_credit_check_table_id, 'SUB', 'READONLY', 'loan_application_id', 4);
    RAISE NOTICE 'Created form: Risk Assessment Form (ID: %)', v_risk_assessment_form_id;

    -- 3.4 Loan Underwriting Task Form (TASK — bound to BPMN userTask ids via dw_form_stage_bindings)
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, config_json, description, bound_table_id
    ) VALUES (
        v_function_unit_id, 'Loan Underwriting Task Form', 'TASK',
        '{"rule":[{"type":"h4","children":["Loan Application Summary"],"native":true},{"type":"input","field":"application_number","title":"Application Number","props":{"disabled":true}},{"type":"input","field":"loan_type","title":"Loan Type","props":{"disabled":true}},{"type":"inputNumber","field":"loan_amount","title":"Loan Amount","props":{"disabled":true,"precision":2}},{"type":"inputNumber","field":"loan_tenure_months","title":"Tenure (months)","props":{"disabled":true,"precision":0}},{"type":"inputNumber","field":"interest_rate","title":"Interest Rate (%)","props":{"disabled":true,"precision":2}},{"type":"inputNumber","field":"emi_amount","title":"EMI Amount","props":{"disabled":true,"precision":2}},{"type":"input","field":"loan_purpose","title":"Loan Purpose","props":{"type":"textarea","disabled":true,"rows":2}},{"type":"input","field":"status","title":"Current Status","props":{"disabled":true}},{"type":"input","field":"risk_rating","title":"Risk Rating","props":{"disabled":true}},{"type":"inputNumber","field":"credit_score","title":"Credit Score","props":{"disabled":true,"precision":0}},{"type":"h4","children":["Applicant Information"],"native":true},{"type":"input","field":"full_name","title":"Full Name","props":{"disabled":true}},{"type":"input","field":"id_number","title":"ID Number","props":{"disabled":true}},{"type":"input","field":"mobile_number","title":"Mobile","props":{"disabled":true}},{"type":"input","field":"email","title":"Email","props":{"disabled":true}},{"type":"h4","children":["Financial Summary"],"native":true},{"type":"input","field":"employment_type","title":"Employment Type","props":{"disabled":true}},{"type":"inputNumber","field":"monthly_income","title":"Monthly Income","props":{"disabled":true,"precision":2}},{"type":"inputNumber","field":"monthly_expenses","title":"Monthly Expenses","props":{"disabled":true,"precision":2}},{"type":"inputNumber","field":"existing_emi","title":"Existing EMI","props":{"disabled":true,"precision":2}},{"type":"h4","children":["Approval Decision"],"native":true},{"type":"input","field":"stage_name","title":"Approval Stage","props":{"placeholder":"Enter stage name","maxlength":100},"validate":[{"required":true,"message":"Stage name is required","trigger":"blur"}]},{"type":"input","field":"approver_name","title":"Approver Name","props":{"placeholder":"Enter approver name","maxlength":100},"validate":[{"required":true,"message":"Approver name is required","trigger":"blur"}]},{"type":"input","field":"approver_role","title":"Approver Role","props":{"placeholder":"Enter role","maxlength":50},"validate":[{"required":true,"message":"Role is required","trigger":"blur"}]},{"type":"select","field":"decision","title":"Decision","props":{"placeholder":"Select decision"},"options":[{"value":"Approve","label":"Approve"},{"value":"Reject","label":"Reject"},{"value":"Return","label":"Return for Revision"}],"validate":[{"required":true,"message":"Decision is required","trigger":"change"}]},{"type":"input","field":"comments","title":"Comments","props":{"type":"textarea","placeholder":"Enter approval comments","rows":3}},{"type":"input","field":"conditions","title":"Conditions","props":{"type":"textarea","placeholder":"Enter any conditions","rows":2}}],"options":{"form":{"labelWidth":"250px","size":"default","labelPosition":"right"},"submitBtn":{"show":true,"innerText":"Submit Decision"}}}'::jsonb,
        'Form for managers to approve loan applications',
        v_loan_application_table_id
    ) RETURNING id INTO v_approval_form_id;

    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order) VALUES
    (v_approval_form_id, v_loan_application_table_id, 'PRIMARY', 'READONLY', NULL, 1),
    (v_approval_form_id, v_applicant_info_table_id, 'SUB', 'READONLY', 'loan_application_id', 2),
    (v_approval_form_id, v_financial_info_table_id, 'SUB', 'READONLY', 'loan_application_id', 3),
    (v_approval_form_id, v_collateral_table_id, 'SUB', 'READONLY', 'loan_application_id', 4),
    (v_approval_form_id, v_credit_check_table_id, 'SUB', 'READONLY', 'loan_application_id', 5),
    (v_approval_form_id, v_approval_history_table_id, 'SUB', 'EDITABLE', 'loan_application_id', 6);
    RAISE NOTICE 'Created form: Loan Underwriting Task Form (ID: %)', v_approval_form_id;

    -- 3.5 Loan Disbursement Task Form
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, config_json, description, bound_table_id
    ) VALUES (
        v_function_unit_id, 'Loan Disbursement Form', 'TASK',
        '{"rule":[{"type":"h4","children":["Loan Details"],"native":true},{"type":"input","field":"application_number","title":"Application Number","props":{"placeholder":"Application number","maxlength":50}},{"type":"input","field":"loan_type","title":"Loan Type","props":{"disabled":true}},{"type":"inputNumber","field":"loan_amount","title":"Approved Amount","props":{"precision":2}},{"type":"inputNumber","field":"interest_rate","title":"Interest Rate (%)","props":{"precision":2}},{"type":"inputNumber","field":"emi_amount","title":"EMI Amount","props":{"precision":2}},{"type":"inputNumber","field":"loan_tenure_months","title":"Tenure (months)","props":{"precision":0}},{"type":"datePicker","field":"disbursement_date","title":"Disbursement Date","props":{"type":"datetime","placeholder":"Select disbursement date","valueFormat":"YYYY-MM-DD HH:mm:ss"},"validate":[{"required":true,"message":"Disbursement date is required","trigger":"blur"}]},{"type":"h4","children":["Applicant Info (Read-only)"],"native":true},{"type":"input","field":"full_name","title":"Full Name","props":{"disabled":true}},{"type":"input","field":"mobile_number","title":"Mobile","props":{"disabled":true}},{"type":"input","field":"email","title":"Email","props":{"disabled":true}},{"type":"h4","children":["Bank Account (Read-only)"],"native":true},{"type":"input","field":"bank_name","title":"Bank Name","props":{"disabled":true}},{"type":"input","field":"account_number","title":"Account Number","props":{"disabled":true}},{"type":"input","field":"account_type","title":"Account Type","props":{"disabled":true}}],"options":{"form":{"labelWidth":"250px","size":"default","labelPosition":"right"},"submitBtn":{"show":true,"innerText":"Process Disbursement"}}}'::jsonb,
        'Form for finance team to process loan disbursement',
        v_loan_application_table_id
    ) RETURNING id INTO v_disbursement_form_id;

    INSERT INTO dw_form_table_bindings (form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order) VALUES
    (v_disbursement_form_id, v_loan_application_table_id, 'PRIMARY', 'EDITABLE', NULL, 1),
    (v_disbursement_form_id, v_applicant_info_table_id, 'SUB', 'READONLY', 'loan_application_id', 2),
    (v_disbursement_form_id, v_financial_info_table_id, 'SUB', 'READONLY', 'loan_application_id', 3);
    RAISE NOTICE 'Created form: Loan Disbursement Form (ID: %)', v_disbursement_form_id;

    -- =========================================================================
    -- Part 3b: Decision (DMN), table relation metadata, task–form stage bindings
    -- =========================================================================

    INSERT INTO dw_decision_definitions (
        function_unit_id, decision_key, decision_name, dmn_xml, hit_policy, description
    ) VALUES (
        v_function_unit_id,
        'loan_risk_tier_en',
        'Loan risk tier (sample)',
        $dmn$
<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" id="dl_en_dmn" name="LoanRiskTier" namespace="http://workflow.platform/dmn">
  <decision id="dec_loan_risk_tier" name="Risk tier from credit score">
    <decisionTable id="dt1" hitPolicy="FIRST">
      <input id="in_score" label="Credit score">
        <inputExpression typeRef="number"><text>credit_score</text></inputExpression>
      </input>
      <output id="out_tier" label="Tier" name="riskTier" typeRef="string"/>
      <rule><inputEntry><text>&gt;= 750</text></inputEntry><outputEntry><text>"LOW"</text></outputEntry></rule>
      <rule><inputEntry><text>&gt;= 650</text></inputEntry><outputEntry><text>"MEDIUM"</text></outputEntry></rule>
      <rule><inputEntry><text>&lt; 650</text></inputEntry><outputEntry><text>"HIGH"</text></outputEntry></rule>
    </decisionTable>
  </decision>
</definitions>
$dmn$,
        'FIRST',
        'Sample DMN for capability showcase; integrate in service layer as needed'
    );

    INSERT INTO dw_table_relations (
        function_unit_id, source_table_id, source_field_name, relation_type, target_table_id, target_field_name
    ) VALUES
    (v_function_unit_id, v_loan_application_table_id, 'id', 'ONE_TO_MANY', v_applicant_info_table_id, 'loan_application_id'),
    (v_function_unit_id, v_loan_application_table_id, 'id', 'ONE_TO_MANY', v_documents_table_id, 'loan_application_id');

    INSERT INTO dw_form_stage_bindings (form_id, stage_id, stage_name) VALUES
    (v_approval_form_id, 'Task_DocumentVerification', 'Document verification'),
    (v_approval_form_id, 'Task_CreditCheck', 'Credit check'),
    (v_approval_form_id, 'Task_RiskAssessment', 'Risk assessment'),
    (v_approval_form_id, 'Task_ManagerApproval', 'Manager approval'),
    (v_approval_form_id, 'Task_SeniorManagerApproval', 'Senior manager approval'),
    (v_disbursement_form_id, 'Task_Disbursement', 'Process disbursement');

    RAISE NOTICE 'Inserted decision definition, table relations, and form stage bindings';

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
    RAISE NOTICE '  - Credit Check Results (sub): %', v_credit_check_table_id;
    RAISE NOTICE '  - Approval History (sub): %', v_approval_history_table_id;
    RAISE NOTICE '  - Documents (sub): %', v_documents_table_id;
    RAISE NOTICE '';
    RAISE NOTICE 'Form Definitions:';
    RAISE NOTICE '  - Loan Application Form: %', v_application_form_id;
    RAISE NOTICE '  - Credit Check Form (popup): %', v_credit_check_form_id;
    RAISE NOTICE '  - Risk Assessment Form (popup): %', v_risk_assessment_form_id;
    RAISE NOTICE '  - Loan Underwriting Task Form: %', v_approval_form_id;
    RAISE NOTICE '  - Loan Disbursement Form (TASK): %', v_disbursement_form_id;
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