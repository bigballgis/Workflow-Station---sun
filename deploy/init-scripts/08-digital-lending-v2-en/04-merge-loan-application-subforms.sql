-- =============================================================================
-- Merge config_json.subForms for Loan Application Form (PROCESS + subTable tabs)
-- Run after 01-create-digital-lending-complete.sql OR on existing DB to fix blank
-- sub designers (FormDesigner reads subForms[bindingId].rule).
-- Idempotent: replaces subForms key on the PROCESS form only.
-- =============================================================================

DO $patch$
DECLARE
    v_fu BIGINT;
    v_form BIGINT;
    v_a BIGINT;
    v_f BIGINT;
    v_c BIGINT;
    v_d BIGINT;
    v_cr BIGINT;
    v_ah BIGINT;
    v_sub TEXT;
BEGIN
    SELECT id INTO v_fu FROM dw_function_units WHERE code = 'DIGITAL_LENDING_V2_EN' LIMIT 1;
    IF v_fu IS NULL THEN
        RAISE EXCEPTION 'Function unit DIGITAL_LENDING_V2_EN not found';
    END IF;

    SELECT id INTO v_form FROM dw_form_definitions
    WHERE function_unit_id = v_fu AND form_name = 'Loan Application Form' AND form_type = 'PROCESS'
    LIMIT 1;
    IF v_form IS NULL THEN
        RAISE EXCEPTION 'Loan Application Form (PROCESS) not found';
    END IF;

    SELECT b.id INTO v_a FROM dw_form_table_bindings b
    JOIN dw_table_definitions t ON b.table_id = t.id
    WHERE b.form_id = v_form AND t.table_name = 'Applicant Information' LIMIT 1;
    SELECT b.id INTO v_f FROM dw_form_table_bindings b
    JOIN dw_table_definitions t ON b.table_id = t.id
    WHERE b.form_id = v_form AND t.table_name = 'Financial Information' LIMIT 1;
    SELECT b.id INTO v_c FROM dw_form_table_bindings b
    JOIN dw_table_definitions t ON b.table_id = t.id
    WHERE b.form_id = v_form AND t.table_name = 'Collateral Details' LIMIT 1;
    SELECT b.id INTO v_d FROM dw_form_table_bindings b
    JOIN dw_table_definitions t ON b.table_id = t.id
    WHERE b.form_id = v_form AND t.table_name = 'Documents' LIMIT 1;
    SELECT b.id INTO v_cr FROM dw_form_table_bindings b
    JOIN dw_table_definitions t ON b.table_id = t.id
    WHERE b.form_id = v_form AND t.table_name = 'Credit Check Results' LIMIT 1;
    SELECT b.id INTO v_ah FROM dw_form_table_bindings b
    JOIN dw_table_definitions t ON b.table_id = t.id
    WHERE b.form_id = v_form AND t.table_name = 'Approval History' LIMIT 1;

    IF v_a IS NULL OR v_f IS NULL OR v_c IS NULL OR v_d IS NULL OR v_cr IS NULL OR v_ah IS NULL THEN
        RAISE EXCEPTION 'Missing SUB bindings for Loan Application Form';
    END IF;

    v_sub := $sf${"__K_APPLICANT__":{"rule":[{"type":"select","field":"applicant_type","title":"Applicant type","props":{"placeholder":"Select applicant type"},"options":[{"value":"Primary","label":"Primary"},{"value":"Co-applicant","label":"Co-applicant"}],"validate":[{"required":true,"message":"Required","trigger":"change"}]},{"type":"input","field":"full_name","title":"Full name","props":{"placeholder":"Full name","maxlength":200},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"datePicker","field":"date_of_birth","title":"Date of birth","props":{"type":"date","placeholder":"Select date","valueFormat":"YYYY-MM-DD"},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"select","field":"gender","title":"Gender","props":{"placeholder":"Select gender"},"options":[{"value":"Male","label":"Male"},{"value":"Female","label":"Female"},{"value":"Other","label":"Other"}],"validate":[{"required":true,"message":"Required","trigger":"change"}]},{"type":"select","field":"marital_status","title":"Marital status","props":{"placeholder":"Select"},"options":[{"value":"Single","label":"Single"},{"value":"Married","label":"Married"},{"value":"Divorced","label":"Divorced"},{"value":"Widowed","label":"Widowed"}],"validate":[{"required":true,"message":"Required","trigger":"change"}]},{"type":"input","field":"nationality","title":"Nationality","props":{"placeholder":"Nationality","maxlength":50},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"id_type","title":"ID document type","props":{"placeholder":"ID type","maxlength":50},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"id_number","title":"ID document number","props":{"placeholder":"ID number","maxlength":50},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"mobile_number","title":"Mobile number","props":{"placeholder":"Mobile","maxlength":20},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"email","title":"Email","props":{"placeholder":"Email","maxlength":100},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"current_address","title":"Current address","props":{"type":"textarea","placeholder":"Address","rows":2},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"permanent_address","title":"Permanent address","props":{"type":"textarea","placeholder":"Permanent address","rows":2}},{"type":"inputNumber","field":"years_at_current_address","title":"Years at current address","props":{"placeholder":"Years","precision":0,"min":0}}],"options":{"form":{"labelWidth":"240px","size":"default","labelPosition":"right"},"submitBtn":{"show":false}}},"__K_FINANCIAL__":{"rule":[{"type":"select","field":"employment_type","title":"Employment type","props":{"placeholder":"Select"},"options":[{"value":"Salaried","label":"Salaried"},{"value":"Self-employed","label":"Self-employed"},{"value":"Business owner","label":"Business owner"}],"validate":[{"required":true,"message":"Required","trigger":"change"}]},{"type":"input","field":"employer_name","title":"Employer / company","props":{"placeholder":"Employer name","maxlength":200}},{"type":"input","field":"occupation","title":"Occupation","props":{"placeholder":"Occupation","maxlength":100},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"inputNumber","field":"years_of_employment","title":"Years of employment","props":{"precision":0,"min":0}},{"type":"inputNumber","field":"monthly_income","title":"Monthly income","props":{"precision":2,"min":0},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"inputNumber","field":"other_income","title":"Other income","props":{"precision":2,"min":0}},{"type":"inputNumber","field":"monthly_expenses","title":"Monthly expenses","props":{"precision":2,"min":0},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"inputNumber","field":"existing_loans","title":"Total existing loans","props":{"precision":2,"min":0}},{"type":"inputNumber","field":"existing_emi","title":"Total existing EMI","props":{"precision":2,"min":0}},{"type":"input","field":"bank_name","title":"Primary bank","props":{"placeholder":"Bank name","maxlength":100},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"account_number","title":"Account number","props":{"placeholder":"Account number","maxlength":50},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"select","field":"account_type","title":"Account type","props":{"placeholder":"Select"},"options":[{"value":"Savings","label":"Savings"},{"value":"Current","label":"Current"}],"validate":[{"required":true,"message":"Required","trigger":"change"}]}],"options":{"form":{"labelWidth":"240px","size":"default","labelPosition":"right"},"submitBtn":{"show":false}}},"__K_COLLATERAL__":{"rule":[{"type":"select","field":"collateral_type","title":"Collateral type","props":{"placeholder":"Select"},"options":[{"value":"Property","label":"Property"},{"value":"Vehicle","label":"Vehicle"},{"value":"Securities","label":"Securities"},{"value":"Fixed Deposit","label":"Fixed Deposit"}],"validate":[{"required":true,"message":"Required","trigger":"change"}]},{"type":"input","field":"collateral_description","title":"Description","props":{"type":"textarea","placeholder":"Describe collateral","rows":3},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"inputNumber","field":"estimated_value","title":"Estimated value","props":{"precision":2,"min":0},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"datePicker","field":"valuation_date","title":"Valuation date","props":{"type":"date","valueFormat":"YYYY-MM-DD"}},{"type":"input","field":"valuer_name","title":"Valuer name","props":{"maxlength":100}},{"type":"input","field":"ownership_proof","title":"Ownership proof","props":{"maxlength":200}},{"type":"input","field":"encumbrance_status","title":"Encumbrance status","props":{"maxlength":50}}],"options":{"form":{"labelWidth":"240px","size":"default","labelPosition":"right"},"submitBtn":{"show":false}}},"__K_DOCUMENTS__":{"rule":[{"type":"input","field":"document_type","title":"Document type","props":{"maxlength":100},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"document_name","title":"Document name","props":{"maxlength":200},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"file_path","title":"File path / URL","props":{"maxlength":500},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"inputNumber","field":"file_size","title":"File size (bytes)","props":{"precision":0,"min":0}},{"type":"datePicker","field":"upload_date","title":"Upload date","props":{"type":"datetime","valueFormat":"YYYY-MM-DD HH:mm:ss"},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"uploaded_by","title":"Uploaded by","props":{"maxlength":100},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"verification_status","title":"Verification status","props":{"maxlength":30}},{"type":"input","field":"verified_by","title":"Verified by","props":{"maxlength":100}},{"type":"datePicker","field":"verification_date","title":"Verification date","props":{"type":"datetime","valueFormat":"YYYY-MM-DD HH:mm:ss"}}],"options":{"form":{"labelWidth":"240px","size":"default","labelPosition":"right"},"submitBtn":{"show":false}}},"__K_CREDIT__":{"rule":[{"type":"input","field":"bureau_name","title":"Credit bureau","props":{"maxlength":100},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"datePicker","field":"check_date","title":"Check date","props":{"type":"datetime","valueFormat":"YYYY-MM-DD HH:mm:ss"},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"inputNumber","field":"credit_score","title":"Credit score","props":{"precision":0,"min":0,"max":999},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"score_range","title":"Score range","props":{"maxlength":50}},{"type":"inputNumber","field":"credit_history_length","title":"Credit history (months)","props":{"precision":0,"min":0}},{"type":"inputNumber","field":"total_accounts","title":"Total accounts","props":{"precision":0,"min":0}},{"type":"inputNumber","field":"active_accounts","title":"Active accounts","props":{"precision":0,"min":0}},{"type":"inputNumber","field":"delinquent_accounts","title":"Delinquent accounts","props":{"precision":0,"min":0}},{"type":"inputNumber","field":"total_debt","title":"Total debt","props":{"precision":2,"min":0}},{"type":"inputNumber","field":"credit_utilization","title":"Credit utilization (%)","props":{"precision":2,"min":0,"max":100}},{"type":"select","field":"payment_history","title":"Payment history","props":{"placeholder":"Select"},"options":[{"value":"Excellent","label":"Excellent"},{"value":"Good","label":"Good"},{"value":"Fair","label":"Fair"},{"value":"Poor","label":"Poor"}]},{"type":"input","field":"remarks","title":"Remarks","props":{"type":"textarea","rows":3}}],"options":{"form":{"labelWidth":"240px","size":"default","labelPosition":"right"},"submitBtn":{"show":false}}},"__K_APPROVAL_HIST__":{"rule":[{"type":"input","field":"stage_name","title":"Stage name","props":{"maxlength":100},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"approver_name","title":"Approver name","props":{"maxlength":100},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"approver_role","title":"Approver role","props":{"maxlength":50},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"action","title":"Action type","props":{"maxlength":30},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"select","field":"decision","title":"Decision","props":{"placeholder":"Select"},"options":[{"value":"Approve","label":"Approve"},{"value":"Reject","label":"Reject"},{"value":"Return","label":"Return"}],"validate":[{"required":true,"message":"Required","trigger":"change"}]},{"type":"datePicker","field":"action_date","title":"Action date","props":{"type":"datetime","valueFormat":"YYYY-MM-DD HH:mm:ss"},"validate":[{"required":true,"message":"Required","trigger":"blur"}]},{"type":"input","field":"comments","title":"Comments","props":{"type":"textarea","rows":3}},{"type":"input","field":"conditions","title":"Conditions","props":{"type":"textarea","rows":2}}],"options":{"form":{"labelWidth":"240px","size":"default","labelPosition":"right"},"submitBtn":{"show":false}}}}$sf$;

    v_sub := replace(v_sub, '"__K_APPLICANT__"', '"' || v_a::text || '"');
    v_sub := replace(v_sub, '"__K_FINANCIAL__"', '"' || v_f::text || '"');
    v_sub := replace(v_sub, '"__K_COLLATERAL__"', '"' || v_c::text || '"');
    v_sub := replace(v_sub, '"__K_DOCUMENTS__"', '"' || v_d::text || '"');
    v_sub := replace(v_sub, '"__K_CREDIT__"', '"' || v_cr::text || '"');
    v_sub := replace(v_sub, '"__K_APPROVAL_HIST__"', '"' || v_ah::text || '"');

    UPDATE dw_form_definitions
    SET config_json = jsonb_set(config_json, '{subForms}', v_sub::jsonb, true),
        updated_at = CURRENT_TIMESTAMP
    WHERE id = v_form;

    RAISE NOTICE 'Merged subForms for Loan Application Form id=% (6 SUB bindings)', v_form;
END
$patch$;
