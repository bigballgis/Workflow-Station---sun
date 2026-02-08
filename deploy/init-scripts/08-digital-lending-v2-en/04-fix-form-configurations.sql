-- =============================================================================
-- 修复表单配置 - 添加字段定义
-- 问题：表单的 config_json 只有布局信息，没有字段列表
-- 解决：为每个表单添加完整的字段配置
-- =============================================================================

DO $$
DECLARE
    v_loan_app_table_id BIGINT;
    v_applicant_table_id BIGINT;
    v_financial_table_id BIGINT;
    v_collateral_table_id BIGINT;
    v_credit_check_table_id BIGINT;
BEGIN
    -- 获取表 ID
    SELECT id INTO v_loan_app_table_id FROM dw_table_definitions 
    WHERE function_unit_id = 10 AND table_name = 'Loan Application';
    
    SELECT id INTO v_applicant_table_id FROM dw_table_definitions 
    WHERE function_unit_id = 10 AND table_name = 'Applicant Information';
    
    SELECT id INTO v_financial_table_id FROM dw_table_definitions 
    WHERE function_unit_id = 10 AND table_name = 'Financial Information';
    
    SELECT id INTO v_collateral_table_id FROM dw_table_definitions 
    WHERE function_unit_id = 10 AND table_name = 'Collateral Details';
    
    SELECT id INTO v_credit_check_table_id FROM dw_table_definitions 
    WHERE function_unit_id = 10 AND table_name = 'Credit Check Results';

    -- 1. 修复 Loan Application Form (ID: 21)
    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
        'size', 'default',
        'layout', 'vertical',
        'labelWidth', '150px',
        'showSubmitButton', true,
        'submitButtonText', '提交申请',
        'fields', jsonb_build_array(
            jsonb_build_object('name', 'loan_type', 'label', 'Loan Type', 'type', 'select', 'required', true, 'options', jsonb_build_array('Personal', 'Home', 'Auto', 'Business')),
            jsonb_build_object('name', 'loan_amount', 'label', 'Loan Amount', 'type', 'number', 'required', true),
            jsonb_build_object('name', 'loan_tenure_months', 'label', 'Tenure (Months)', 'type', 'number', 'required', true),
            jsonb_build_object('name', 'loan_purpose', 'label', 'Purpose', 'type', 'textarea', 'required', true),
            jsonb_build_object('name', 'full_name', 'label', 'Full Name', 'type', 'text', 'required', true),
            jsonb_build_object('name', 'mobile_number', 'label', 'Mobile', 'type', 'text', 'required', true),
            jsonb_build_object('name', 'email', 'label', 'Email', 'type', 'email', 'required', true),
            jsonb_build_object('name', 'monthly_income', 'label', 'Monthly Income', 'type', 'number', 'required', true)
        )
    )
    WHERE id = 21;

    RAISE NOTICE '已修复表单: Loan Application Form (ID: 21)';

    -- 2. 修复 Credit Check Form (ID: 22)
    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
        'size', 'default',
        'layout', 'vertical',
        'labelWidth', '150px',
        'fields', jsonb_build_array(
            jsonb_build_object('name', 'credit_score', 'label', 'Credit Score', 'type', 'number', 'required', true),
            jsonb_build_object('name', 'credit_history_years', 'label', 'Credit History (Years)', 'type', 'number', 'required', true),
            jsonb_build_object('name', 'payment_history', 'label', 'Payment History', 'type', 'select', 'required', true, 'options', jsonb_build_array('Excellent', 'Good', 'Fair', 'Poor')),
            jsonb_build_object('name', 'remarks', 'label', 'Remarks', 'type', 'textarea', 'required', false)
        )
    )
    WHERE id = 22;

    RAISE NOTICE '已修复表单: Credit Check Form (ID: 22)';

    -- 3. 修复 Risk Assessment Form (ID: 23)
    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
        'size', 'default',
        'layout', 'vertical',
        'labelWidth', '150px',
        'fields', jsonb_build_array(
            jsonb_build_object('name', 'risk_rating', 'label', 'Risk Rating', 'type', 'select', 'required', true, 'options', jsonb_build_array('Low', 'Medium', 'High')),
            jsonb_build_object('name', 'risk_factors', 'label', 'Risk Factors', 'type', 'textarea', 'required', true),
            jsonb_build_object('name', 'mitigation_measures', 'label', 'Mitigation Measures', 'type', 'textarea', 'required', false)
        )
    )
    WHERE id = 23;

    RAISE NOTICE '已修复表单: Risk Assessment Form (ID: 23)';

    -- 4. 修复 Loan Approval Form (ID: 24)
    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
        'size', 'default',
        'layout', 'vertical',
        'labelWidth', '150px',
        'fields', jsonb_build_array(
            jsonb_build_object('name', 'approved_amount', 'label', 'Approved Amount', 'type', 'number', 'required', true),
            jsonb_build_object('name', 'interest_rate', 'label', 'Interest Rate (%)', 'type', 'number', 'required', true),
            jsonb_build_object('name', 'approval_comments', 'label', 'Comments', 'type', 'textarea', 'required', false)
        )
    )
    WHERE id = 24;

    RAISE NOTICE '已修复表单: Loan Approval Form (ID: 24)';

    -- 5. 修复 Loan Disbursement Form (ID: 25)
    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
        'size', 'default',
        'layout', 'vertical',
        'labelWidth', '150px',
        'fields', jsonb_build_array(
            jsonb_build_object('name', 'disbursement_amount', 'label', 'Disbursement Amount', 'type', 'number', 'required', true),
            jsonb_build_object('name', 'disbursement_method', 'label', 'Method', 'type', 'select', 'required', true, 'options', jsonb_build_array('Bank Transfer', 'Check', 'Cash')),
            jsonb_build_object('name', 'account_number', 'label', 'Account Number', 'type', 'text', 'required', true)
        )
    )
    WHERE id = 25;

    RAISE NOTICE '已修复表单: Loan Disbursement Form (ID: 25)';

    RAISE NOTICE '========================================';
    RAISE NOTICE '表单配置修复完成！';
    RAISE NOTICE '========================================';
END $$;
