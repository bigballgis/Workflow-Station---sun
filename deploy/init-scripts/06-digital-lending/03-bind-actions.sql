-- =============================================================================
-- Bind Actions to Process Tasks for Digital Lending System
-- =============================================================================

DO $$
DECLARE
    v_function_unit_id BIGINT;
    v_process_id BIGINT;
    
    v_submit_action_id BIGINT;
    v_credit_check_action_id BIGINT;
    v_approve_action_id BIGINT;
    v_reject_action_id BIGINT;
    v_request_info_action_id BIGINT;
    v_view_credit_action_id BIGINT;
    v_assess_risk_action_id BIGINT;
    v_verify_docs_action_id BIGINT;
    v_disburse_action_id BIGINT;
    v_withdraw_action_id BIGINT;
BEGIN
    -- Get Function Unit ID
    SELECT id INTO v_function_unit_id 
    FROM public.dw_function_units 
    WHERE code = 'DIGITAL_LENDING';

    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit DIGITAL_LENDING not found.';
    END IF;

    -- Get Process ID
    SELECT id INTO v_process_id 
    FROM public.dw_process_definitions 
    WHERE function_unit_id = v_function_unit_id
    LIMIT 1;

    IF v_process_id IS NULL THEN
        RAISE EXCEPTION 'Process definition not found for DIGITAL_LENDING.';
    END IF;

    RAISE NOTICE 'Binding actions for Function Unit ID: %, Process ID: %', v_function_unit_id, v_process_id;

    -- Get Action IDs
    SELECT id INTO v_submit_action_id FROM public.dw_action_definitions 
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Submit Loan Application';
    
    SELECT id INTO v_credit_check_action_id FROM public.dw_action_definitions 
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Perform Credit Check';
    
    SELECT id INTO v_approve_action_id FROM public.dw_action_definitions 
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Approve Loan';
    
    SELECT id INTO v_reject_action_id FROM public.dw_action_definitions 
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Reject Loan';
    
    SELECT id INTO v_request_info_action_id FROM public.dw_action_definitions 
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Request Additional Info';
    
    SELECT id INTO v_view_credit_action_id FROM public.dw_action_definitions 
    WHERE function_unit_id = v_function_unit_id AND action_name = 'View Credit Report';
    
    SELECT id INTO v_assess_risk_action_id FROM public.dw_action_definitions 
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Assess Risk';
    
    SELECT id INTO v_verify_docs_action_id FROM public.dw_action_definitions 
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Verify Documents';
    
    SELECT id INTO v_disburse_action_id FROM public.dw_action_definitions 
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Process Disbursement';
    
    SELECT id INTO v_withdraw_action_id FROM public.dw_action_definitions 
    WHERE function_unit_id = v_function_unit_id AND action_name = 'Withdraw Application';

    -- =============================================================================
    -- Bind Actions to Tasks
    -- =============================================================================
    
    -- Task: Submit Application
    -- Actions: Submit, Withdraw
    INSERT INTO public.dw_action_bindings (
        function_unit_id, action_id, binding_type, binding_target, sort_order
    ) VALUES
    (v_function_unit_id, v_submit_action_id, 'TASK', 'Task_SubmitApplication', 1),
    (v_function_unit_id, v_withdraw_action_id, 'TASK', 'Task_SubmitApplication', 2);

    -- Task: Document Verification
    -- Actions: Verify Documents, Approve, Reject, Request Additional Info
    INSERT INTO public.dw_action_bindings (
        function_unit_id, action_id, binding_type, binding_target, sort_order
    ) VALUES
    (v_function_unit_id, v_verify_docs_action_id, 'TASK', 'Task_DocumentVerification', 1),
    (v_function_unit_id, v_approve_action_id, 'TASK', 'Task_DocumentVerification', 2),
    (v_function_unit_id, v_reject_action_id, 'TASK', 'Task_DocumentVerification', 3),
    (v_function_unit_id, v_request_info_action_id, 'TASK', 'Task_DocumentVerification', 4);

    -- Task: Credit Check
    -- Actions: Perform Credit Check (FORM POPUP), View Credit Report, Approve, Request Additional Info
    INSERT INTO public.dw_action_bindings (
        function_unit_id, action_id, binding_type, binding_target, sort_order
    ) VALUES
    (v_function_unit_id, v_credit_check_action_id, 'TASK', 'Task_CreditCheck', 1),
    (v_function_unit_id, v_view_credit_action_id, 'TASK', 'Task_CreditCheck', 2),
    (v_function_unit_id, v_approve_action_id, 'TASK', 'Task_CreditCheck', 3),
    (v_function_unit_id, v_request_info_action_id, 'TASK', 'Task_CreditCheck', 4);

    -- Task: Risk Assessment
    -- Actions: Assess Risk (FORM POPUP), View Credit Report, Approve, Reject, Request Additional Info
    INSERT INTO public.dw_action_bindings (
        function_unit_id, action_id, binding_type, binding_target, sort_order
    ) VALUES
    (v_function_unit_id, v_assess_risk_action_id, 'TASK', 'Task_RiskAssessment', 1),
    (v_function_unit_id, v_view_credit_action_id, 'TASK', 'Task_RiskAssessment', 2),
    (v_function_unit_id, v_approve_action_id, 'TASK', 'Task_RiskAssessment', 3),
    (v_function_unit_id, v_reject_action_id, 'TASK', 'Task_RiskAssessment', 4),
    (v_function_unit_id, v_request_info_action_id, 'TASK', 'Task_RiskAssessment', 5);

    -- Task: Manager Approval
    -- Actions: Approve, Reject, Request Additional Info, View Credit Report
    INSERT INTO public.dw_action_bindings (
        function_unit_id, action_id, binding_type, binding_target, sort_order
    ) VALUES
    (v_function_unit_id, v_approve_action_id, 'TASK', 'Task_ManagerApproval', 1),
    (v_function_unit_id, v_reject_action_id, 'TASK', 'Task_ManagerApproval', 2),
    (v_function_unit_id, v_request_info_action_id, 'TASK', 'Task_ManagerApproval', 3),
    (v_function_unit_id, v_view_credit_action_id, 'TASK', 'Task_ManagerApproval', 4);

    -- Task: Senior Manager Approval
    -- Actions: Approve, Reject, Request Additional Info, View Credit Report
    INSERT INTO public.dw_action_bindings (
        function_unit_id, action_id, binding_type, binding_target, sort_order
    ) VALUES
    (v_function_unit_id, v_approve_action_id, 'TASK', 'Task_SeniorManagerApproval', 1),
    (v_function_unit_id, v_reject_action_id, 'TASK', 'Task_SeniorManagerApproval', 2),
    (v_function_unit_id, v_request_info_action_id, 'TASK', 'Task_SeniorManagerApproval', 3),
    (v_function_unit_id, v_view_credit_action_id, 'TASK', 'Task_SeniorManagerApproval', 4);

    -- Task: Disbursement
    -- Actions: Process Disbursement, View Credit Report
    INSERT INTO public.dw_action_bindings (
        function_unit_id, action_id, binding_type, binding_target, sort_order
    ) VALUES
    (v_function_unit_id, v_disburse_action_id, 'TASK', 'Task_Disbursement', 1),
    (v_function_unit_id, v_view_credit_action_id, 'TASK', 'Task_Disbursement', 2);

    RAISE NOTICE 'Action bindings created successfully';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Digital Lending System Setup Complete!';
    RAISE NOTICE '========================================';
    RAISE NOTICE '';
    RAISE NOTICE 'Next Steps:';
    RAISE NOTICE '  1. Access Developer Workstation at http://localhost:3002';
    RAISE NOTICE '  2. Navigate to Function Units';
    RAISE NOTICE '  3. Find "Digital Lending System"';
    RAISE NOTICE '  4. Deploy the function unit';
    RAISE NOTICE '  5. Test the workflow in User Portal';
    RAISE NOTICE '';
    RAISE NOTICE 'Key Features Demonstrated:';
    RAISE NOTICE '  ✓ 7 Tables (Main, Sub, Related)';
    RAISE NOTICE '  ✓ 5 Forms (including POPUP forms)';
    RAISE NOTICE '  ✓ Complex BPMN workflow with multiple approval stages';
    RAISE NOTICE '  ✓ 12 Actions including FORM_POPUP actions';
    RAISE NOTICE '  ✓ Action bindings to workflow tasks';
    RAISE NOTICE '========================================';
END $$;
