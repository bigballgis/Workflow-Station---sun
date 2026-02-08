-- =============================================================================
-- 修复表单配置格式 - 转换为 form-create 格式
-- 前端期望的是 { rule: [...], options: {...} } 格式
-- 而不是 { fields: [...] } 格式
-- =============================================================================

DO $
BEGIN
    -- 1. 修复 Loan Application Form (ID: 21)
    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object('type', 'select', 'field', 'loan_type', 'title', 'Loan Type', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please select loan type')), 'options', jsonb_build_array(jsonb_build_object('label', 'Personal', 'value', 'Personal'), jsonb_build_object('label', 'Home', 'value', 'Home'), jsonb_build_object('label', 'Auto', 'value', 'Auto'), jsonb_build_object('label', 'Business', 'value', 'Business'))),
            jsonb_build_object('type', 'inputNumber', 'field', 'loan_amount', 'title', 'Loan Amount', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter loan amount')), 'props', jsonb_build_object('min', 0)),
            jsonb_build_object('type', 'inputNumber', 'field', 'loan_tenure_months', 'title', 'Tenure (Months)', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter tenure')), 'props', jsonb_build_object('min', 1)),
            jsonb_build_object('type', 'input', 'field', 'loan_purpose', 'title', 'Purpose', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter purpose')), 'props', jsonb_build_object('type', 'textarea', 'rows', 3)),
            jsonb_build_object('type', 'input', 'field', 'full_name', 'title', 'Full Name', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter full name'))),
            jsonb_build_object('type', 'input', 'field', 'mobile_number', 'title', 'Mobile', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter mobile number'))),
            jsonb_build_object('type', 'input', 'field', 'email', 'title', 'Email', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter email'), jsonb_build_object('type', 'email', 'message', 'Please enter valid email'))),
            jsonb_build_object('type', 'inputNumber', 'field', 'monthly_income', 'title', 'Monthly Income', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter monthly income')), 'props', jsonb_build_object('min', 0))
        ),
        'options', jsonb_build_object(
            'form', jsonb_build_object(
                'labelPosition', 'top',
                'size', 'default',
                'labelWidth', '150px'
            ),
            'submitBtn', jsonb_build_object(
                'show', true,
                'innerText', 'Submit Application'
            )
        )
    )
    WHERE id = 21;

    RAISE NOTICE '已修复表单: Loan Application Form (ID: 21)';

    -- 2. 修复 Credit Check Form (ID: 22)
    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object('type', 'inputNumber', 'field', 'credit_score', 'title', 'Credit Score', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter credit score')), 'props', jsonb_build_object('min', 300, 'max', 850)),
            jsonb_build_object('type', 'inputNumber', 'field', 'credit_history_years', 'title', 'Credit History (Years)', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter credit history')), 'props', jsonb_build_object('min', 0)),
            jsonb_build_object('type', 'select', 'field', 'payment_history', 'title', 'Payment History', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please select payment history')), 'options', jsonb_build_array(jsonb_build_object('label', 'Excellent', 'value', 'Excellent'), jsonb_build_object('label', 'Good', 'value', 'Good'), jsonb_build_object('label', 'Fair', 'value', 'Fair'), jsonb_build_object('label', 'Poor', 'value', 'Poor'))),
            jsonb_build_object('type', 'input', 'field', 'remarks', 'title', 'Remarks', 'props', jsonb_build_object('type', 'textarea', 'rows', 3))
        ),
        'options', jsonb_build_object(
            'form', jsonb_build_object(
                'labelPosition', 'top',
                'size', 'default',
                'labelWidth', '150px'
            )
        )
    )
    WHERE id = 22;

    RAISE NOTICE '已修复表单: Credit Check Form (ID: 22)';

    -- 3. 修复 Risk Assessment Form (ID: 23)
    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object('type', 'select', 'field', 'risk_rating', 'title', 'Risk Rating', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please select risk rating')), 'options', jsonb_build_array(jsonb_build_object('label', 'Low', 'value', 'Low'), jsonb_build_object('label', 'Medium', 'value', 'Medium'), jsonb_build_object('label', 'High', 'value', 'High'))),
            jsonb_build_object('type', 'input', 'field', 'risk_factors', 'title', 'Risk Factors', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter risk factors')), 'props', jsonb_build_object('type', 'textarea', 'rows', 3)),
            jsonb_build_object('type', 'input', 'field', 'mitigation_measures', 'title', 'Mitigation Measures', 'props', jsonb_build_object('type', 'textarea', 'rows', 3))
        ),
        'options', jsonb_build_object(
            'form', jsonb_build_object(
                'labelPosition', 'top',
                'size', 'default',
                'labelWidth', '150px'
            )
        )
    )
    WHERE id = 23;

    RAISE NOTICE '已修复表单: Risk Assessment Form (ID: 23)';

    -- 4. 修复 Loan Approval Form (ID: 24)
    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object('type', 'inputNumber', 'field', 'approved_amount', 'title', 'Approved Amount', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter approved amount')), 'props', jsonb_build_object('min', 0)),
            jsonb_build_object('type', 'inputNumber', 'field', 'interest_rate', 'title', 'Interest Rate (%)', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter interest rate')), 'props', jsonb_build_object('min', 0, 'max', 100, 'precision', 2)),
            jsonb_build_object('type', 'input', 'field', 'approval_comments', 'title', 'Comments', 'props', jsonb_build_object('type', 'textarea', 'rows', 3))
        ),
        'options', jsonb_build_object(
            'form', jsonb_build_object(
                'labelPosition', 'top',
                'size', 'default',
                'labelWidth', '150px'
            )
        )
    )
    WHERE id = 24;

    RAISE NOTICE '已修复表单: Loan Approval Form (ID: 24)';

    -- 5. 修复 Loan Disbursement Form (ID: 25)
    UPDATE dw_form_definitions
    SET config_json = jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object('type', 'inputNumber', 'field', 'disbursement_amount', 'title', 'Disbursement Amount', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter disbursement amount')), 'props', jsonb_build_object('min', 0)),
            jsonb_build_object('type', 'select', 'field', 'disbursement_method', 'title', 'Method', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please select disbursement method')), 'options', jsonb_build_array(jsonb_build_object('label', 'Bank Transfer', 'value', 'Bank Transfer'), jsonb_build_object('label', 'Check', 'value', 'Check'), jsonb_build_object('label', 'Cash', 'value', 'Cash'))),
            jsonb_build_object('type', 'input', 'field', 'account_number', 'title', 'Account Number', 'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', 'Please enter account number')))
        ),
        'options', jsonb_build_object(
            'form', jsonb_build_object(
                'labelPosition', 'top',
                'size', 'default',
                'labelWidth', '150px'
            )
        )
    )
    WHERE id = 25;

    RAISE NOTICE '已修复表单: Loan Disbursement Form (ID: 25)';

    RAISE NOTICE '========================================';
    RAISE NOTICE '表单格式修复完成！已转换为 form-create 格式';
    RAISE NOTICE '========================================';
END $;
