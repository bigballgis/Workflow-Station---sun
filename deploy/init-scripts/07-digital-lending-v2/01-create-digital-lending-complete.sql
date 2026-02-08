-- =============================================================================
-- 数字贷款系统 (Digital Lending System) - 完整版
-- 基于 AI 功能单元生成框架创建
-- 
-- 功能特性：
-- 1. 完整的数据模型（7个表）
-- 2. 多种表单类型（主表单、弹窗表单）
-- 3. 复杂的审批流程（8个节点）
-- 4. 丰富的业务动作（15个动作）
-- 5. 多种任务分配方式
-- 6. 表单弹窗操作
-- =============================================================================

DO $$
DECLARE
    -- 功能单元
    v_function_unit_id BIGINT;
    
    -- 表 ID
    v_loan_application_table_id BIGINT;
    v_applicant_info_table_id BIGINT;
    v_financial_info_table_id BIGINT;
    v_collateral_table_id BIGINT;
    v_credit_check_table_id BIGINT;
    v_approval_history_table_id BIGINT;
    v_documents_table_id BIGINT;
    
    -- 表单 ID
    v_application_form_id BIGINT;
    v_credit_check_form_id BIGINT;
    v_risk_assessment_form_id BIGINT;
    v_approval_form_id BIGINT;
    v_disbursement_form_id BIGINT;
    
    -- 流程 ID
    v_process_id BIGINT;
BEGIN

    -- =========================================================================
    -- 第一部分：创建功能单元
    -- =========================================================================
    
    INSERT INTO dw_function_units (
        code,
        name,
        description,
        status,
        version,
        is_active,
        deployed_at,
        created_by,
        created_at,
        updated_at
    ) VALUES (
        'DIGITAL_LENDING_V2',
        '数字贷款系统 V2',
        '全功能数字贷款申请和审批系统，包含信用检查、风险评估、抵押物管理、多级审批和自动放款功能',
        'DRAFT',
        '1.0.0',
        true,
        CURRENT_TIMESTAMP,
        'system',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    ) RETURNING id INTO v_function_unit_id;

    RAISE NOTICE '========================================';
    RAISE NOTICE '创建功能单元：数字贷款系统 V2';
    RAISE NOTICE '功能单元 ID: %', v_function_unit_id;
    RAISE NOTICE '========================================';

    -- =========================================================================
    -- 第二部分：创建表定义
    -- =========================================================================
    
    -- 2.1 主表：贷款申请
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id,
        'Loan Application',
        'MAIN',
        '贷款申请主表，记录贷款的核心信息'
    ) RETURNING id INTO v_loan_application_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_loan_application_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, '主键', 1),
    (v_loan_application_table_id, 'application_number', 'VARCHAR', 50, NULL, FALSE, '申请编号（唯一）', 2),
    (v_loan_application_table_id, 'application_date', 'TIMESTAMP', NULL, NULL, FALSE, '申请日期', 3),
    (v_loan_application_table_id, 'loan_type', 'VARCHAR', 50, NULL, FALSE, '贷款类型（个人/房贷/车贷/企业）', 4),
    (v_loan_application_table_id, 'loan_amount', 'DECIMAL', 15, 2, FALSE, '申请金额', 5),
    (v_loan_application_table_id, 'loan_tenure_months', 'INTEGER', NULL, NULL, FALSE, '贷款期限（月）', 6),
    (v_loan_application_table_id, 'interest_rate', 'DECIMAL', 5, 2, TRUE, '年利率（%）', 7),
    (v_loan_application_table_id, 'emi_amount', 'DECIMAL', 15, 2, TRUE, '月供金额', 8),
    (v_loan_application_table_id, 'loan_purpose', 'TEXT', NULL, NULL, FALSE, '贷款用途', 9),
    (v_loan_application_table_id, 'status', 'VARCHAR', 30, NULL, FALSE, '申请状态', 10),
    (v_loan_application_table_id, 'current_stage', 'VARCHAR', 50, NULL, TRUE, '当前流程阶段', 11),
    (v_loan_application_table_id, 'risk_rating', 'VARCHAR', 20, NULL, TRUE, '风险评级（低/中/高）', 12),
    (v_loan_application_table_id, 'credit_score', 'INTEGER', NULL, NULL, TRUE, '信用评分', 13),
    (v_loan_application_table_id, 'approval_date', 'TIMESTAMP', NULL, NULL, TRUE, '批准日期', 14),
    (v_loan_application_table_id, 'disbursement_date', 'TIMESTAMP', NULL, NULL, TRUE, '放款日期', 15),
    (v_loan_application_table_id, 'rejection_reason', 'TEXT', NULL, NULL, TRUE, '拒绝原因', 16),
    (v_loan_application_table_id, 'created_by', 'VARCHAR', 100, NULL, FALSE, '创建人', 17),
    (v_loan_application_table_id, 'created_at', 'TIMESTAMP', NULL, NULL, FALSE, '创建时间', 18),
    (v_loan_application_table_id, 'updated_at', 'TIMESTAMP', NULL, NULL, TRUE, '更新时间', 19);

    RAISE NOTICE '创建主表：贷款申请 (ID: %)', v_loan_application_table_id;

    -- 2.2 子表：申请人信息
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id,
        'Applicant Information',
        'SUB',
        '申请人个人信息（支持主申请人和共同申请人）'
    ) RETURNING id INTO v_applicant_info_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_applicant_info_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, '主键', 1),
    (v_applicant_info_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, '外键：贷款申请ID', 2),
    (v_applicant_info_table_id, 'applicant_type', 'VARCHAR', 20, NULL, FALSE, '申请人类型（主申请人/共同申请人）', 3),
    (v_applicant_info_table_id, 'full_name', 'VARCHAR', 200, NULL, FALSE, '全名', 4),
    (v_applicant_info_table_id, 'date_of_birth', 'DATE', NULL, NULL, FALSE, '出生日期', 5),
    (v_applicant_info_table_id, 'gender', 'VARCHAR', 20, NULL, FALSE, '性别', 6),
    (v_applicant_info_table_id, 'marital_status', 'VARCHAR', 20, NULL, FALSE, '婚姻状况', 7),
    (v_applicant_info_table_id, 'nationality', 'VARCHAR', 50, NULL, FALSE, '国籍', 8),
    (v_applicant_info_table_id, 'id_type', 'VARCHAR', 50, NULL, FALSE, '证件类型', 9),
    (v_applicant_info_table_id, 'id_number', 'VARCHAR', 50, NULL, FALSE, '证件号码', 10),
    (v_applicant_info_table_id, 'mobile_number', 'VARCHAR', 20, NULL, FALSE, '手机号码', 11),
    (v_applicant_info_table_id, 'email', 'VARCHAR', 100, NULL, FALSE, '电子邮箱', 12),
    (v_applicant_info_table_id, 'current_address', 'TEXT', NULL, NULL, FALSE, '当前居住地址', 13),
    (v_applicant_info_table_id, 'permanent_address', 'TEXT', NULL, NULL, TRUE, '永久地址', 14),
    (v_applicant_info_table_id, 'years_at_current_address', 'INTEGER', NULL, NULL, TRUE, '在当前地址居住年限', 15);

    RAISE NOTICE '创建子表：申请人信息 (ID: %)', v_applicant_info_table_id;

    -- 2.3 子表：财务信息
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id,
        'Financial Information',
        'SUB',
        '申请人财务状况信息'
    ) RETURNING id INTO v_financial_info_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_financial_info_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, '主键', 1),
    (v_financial_info_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, '外键：贷款申请ID', 2),
    (v_financial_info_table_id, 'employment_type', 'VARCHAR', 50, NULL, FALSE, '就业类型（受薪/自雇/企业主）', 3),
    (v_financial_info_table_id, 'employer_name', 'VARCHAR', 200, NULL, TRUE, '雇主/公司名称', 4),
    (v_financial_info_table_id, 'occupation', 'VARCHAR', 100, NULL, FALSE, '职业/职位', 5),
    (v_financial_info_table_id, 'years_of_employment', 'INTEGER', NULL, NULL, TRUE, '工作年限', 6),
    (v_financial_info_table_id, 'monthly_income', 'DECIMAL', 15, 2, FALSE, '月收入', 7),
    (v_financial_info_table_id, 'other_income', 'DECIMAL', 15, 2, TRUE, '其他收入', 8),
    (v_financial_info_table_id, 'monthly_expenses', 'DECIMAL', 15, 2, FALSE, '月支出', 9),
    (v_financial_info_table_id, 'existing_loans', 'DECIMAL', 15, 2, TRUE, '现有贷款总额', 10),
    (v_financial_info_table_id, 'existing_emi', 'DECIMAL', 15, 2, TRUE, '现有月供总额', 11),
    (v_financial_info_table_id, 'bank_name', 'VARCHAR', 100, NULL, FALSE, '主要银行名称', 12),
    (v_financial_info_table_id, 'account_number', 'VARCHAR', 50, NULL, FALSE, '银行账号', 13),
    (v_financial_info_table_id, 'account_type', 'VARCHAR', 30, NULL, FALSE, '账户类型（储蓄/活期）', 14);

    RAISE NOTICE '创建子表：财务信息 (ID: %)', v_financial_info_table_id;

    -- 2.4 子表：抵押物信息
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id,
        'Collateral Details',
        'SUB',
        '抵押物/担保品详细信息（用于有抵押贷款）'
    ) RETURNING id INTO v_collateral_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_collateral_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, '主键', 1),
    (v_collateral_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, '外键：贷款申请ID', 2),
    (v_collateral_table_id, 'collateral_type', 'VARCHAR', 50, NULL, FALSE, '抵押物类型（房产/车辆/证券/定期存款）', 3),
    (v_collateral_table_id, 'collateral_description', 'TEXT', NULL, NULL, FALSE, '抵押物详细描述', 4),
    (v_collateral_table_id, 'estimated_value', 'DECIMAL', 15, 2, FALSE, '估值', 5),
    (v_collateral_table_id, 'valuation_date', 'DATE', NULL, NULL, TRUE, '估值日期', 6),
    (v_collateral_table_id, 'valuer_name', 'VARCHAR', 100, NULL, TRUE, '估值师姓名', 7),
    (v_collateral_table_id, 'ownership_proof', 'VARCHAR', 200, NULL, TRUE, '所有权证明文件', 8),
    (v_collateral_table_id, 'encumbrance_status', 'VARCHAR', 50, NULL, TRUE, '产权负担状态', 9);

    RAISE NOTICE '创建子表：抵押物信息 (ID: %)', v_collateral_table_id;

    -- 2.5 关联表：信用检查结果
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id,
        'Credit Check Results',
        'RELATION',
        '信用局检查结果记录'
    ) RETURNING id INTO v_credit_check_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_credit_check_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, '主键', 1),
    (v_credit_check_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, '外键：贷款申请ID', 2),
    (v_credit_check_table_id, 'bureau_name', 'VARCHAR', 100, NULL, FALSE, '信用局名称', 3),
    (v_credit_check_table_id, 'check_date', 'TIMESTAMP', NULL, NULL, FALSE, '检查日期', 4),
    (v_credit_check_table_id, 'credit_score', 'INTEGER', NULL, NULL, FALSE, '信用评分', 5),
    (v_credit_check_table_id, 'score_range', 'VARCHAR', 50, NULL, TRUE, '评分范围（如：300-900）', 6),
    (v_credit_check_table_id, 'credit_history_length', 'INTEGER', NULL, NULL, TRUE, '信用历史长度（月）', 7),
    (v_credit_check_table_id, 'total_accounts', 'INTEGER', NULL, NULL, TRUE, '总账户数', 8),
    (v_credit_check_table_id, 'active_accounts', 'INTEGER', NULL, NULL, TRUE, '活跃账户数', 9),
    (v_credit_check_table_id, 'delinquent_accounts', 'INTEGER', NULL, NULL, TRUE, '逾期账户数', 10),
    (v_credit_check_table_id, 'total_debt', 'DECIMAL', 15, 2, TRUE, '总债务', 11),
    (v_credit_check_table_id, 'credit_utilization', 'DECIMAL', 5, 2, TRUE, '信用使用率（%）', 12),
    (v_credit_check_table_id, 'payment_history', 'VARCHAR', 20, NULL, TRUE, '还款历史评级', 13),
    (v_credit_check_table_id, 'remarks', 'TEXT', NULL, NULL, TRUE, '备注', 14);

    RAISE NOTICE '创建关联表：信用检查结果 (ID: %)', v_credit_check_table_id;

    -- 2.6 关联表：审批历史
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id,
        'Approval History',
        'RELATION',
        '所有审批操作的历史记录'
    ) RETURNING id INTO v_approval_history_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_approval_history_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, '主键', 1),
    (v_approval_history_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, '外键：贷款申请ID', 2),
    (v_approval_history_table_id, 'stage_name', 'VARCHAR', 100, NULL, FALSE, '审批阶段名称', 3),
    (v_approval_history_table_id, 'approver_name', 'VARCHAR', 100, NULL, FALSE, '审批人姓名', 4),
    (v_approval_history_table_id, 'approver_role', 'VARCHAR', 50, NULL, FALSE, '审批人角色', 5),
    (v_approval_history_table_id, 'action', 'VARCHAR', 30, NULL, FALSE, '操作类型', 6),
    (v_approval_history_table_id, 'decision', 'VARCHAR', 20, NULL, FALSE, '决定（批准/拒绝/退回）', 7),
    (v_approval_history_table_id, 'action_date', 'TIMESTAMP', NULL, NULL, FALSE, '操作日期', 8),
    (v_approval_history_table_id, 'comments', 'TEXT', NULL, NULL, TRUE, '审批意见', 9),
    (v_approval_history_table_id, 'conditions', 'TEXT', NULL, NULL, TRUE, '批准条件', 10);

    RAISE NOTICE '创建关联表：审批历史 (ID: %)', v_approval_history_table_id;

    -- 2.7 关联表：文档附件
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_type, description
    ) VALUES (
        v_function_unit_id,
        'Documents',
        'RELATION',
        '贷款申请的支持文档和附件'
    ) RETURNING id INTO v_documents_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, scale, nullable, description, sort_order
    ) VALUES
    (v_documents_table_id, 'id', 'BIGINT', NULL, NULL, FALSE, '主键', 1),
    (v_documents_table_id, 'loan_application_id', 'BIGINT', NULL, NULL, FALSE, '外键：贷款申请ID', 2),
    (v_documents_table_id, 'document_type', 'VARCHAR', 100, NULL, FALSE, '文档类型', 3),
    (v_documents_table_id, 'document_name', 'VARCHAR', 200, NULL, FALSE, '文档名称', 4),
    (v_documents_table_id, 'file_path', 'VARCHAR', 500, NULL, FALSE, '文件存储路径', 5),
    (v_documents_table_id, 'file_size', 'BIGINT', NULL, NULL, TRUE, '文件大小（字节）', 6),
    (v_documents_table_id, 'upload_date', 'TIMESTAMP', NULL, NULL, FALSE, '上传日期', 7),
    (v_documents_table_id, 'uploaded_by', 'VARCHAR', 100, NULL, FALSE, '上传人', 8),
    (v_documents_table_id, 'verification_status', 'VARCHAR', 30, NULL, TRUE, '验证状态', 9),
    (v_documents_table_id, 'verified_by', 'VARCHAR', 100, NULL, TRUE, '验证人', 10),
    (v_documents_table_id, 'verification_date', 'TIMESTAMP', NULL, NULL, TRUE, '验证日期', 11);

    RAISE NOTICE '创建关联表：文档附件 (ID: %)', v_documents_table_id;

    -- =========================================================================
    -- 第三部分：创建表单定义
    -- =========================================================================
    
    -- 3.1 贷款申请表单（主表单）
    INSERT INTO dw_form_definitions (
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
            "submitButtonText": "提交申请"
        }'::jsonb,
        '完整的贷款申请表单，供客户填写',
        v_loan_application_table_id
    ) RETURNING id INTO v_application_form_id;

    -- 绑定表到申请表单
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, sort_order
    ) VALUES
    (v_application_form_id, v_loan_application_table_id, 'PRIMARY', 'EDITABLE', 1),
    (v_application_form_id, v_applicant_info_table_id, 'SUB', 'EDITABLE', 2),
    (v_application_form_id, v_financial_info_table_id, 'SUB', 'EDITABLE', 3),
    (v_application_form_id, v_collateral_table_id, 'SUB', 'EDITABLE', 4),
    (v_application_form_id, v_documents_table_id, 'RELATED', 'EDITABLE', 5);

    RAISE NOTICE '创建表单：贷款申请表单 (ID: %)', v_application_form_id;

    -- 3.2 信用检查表单（弹窗表单）
    INSERT INTO dw_form_definitions (
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
            "title": "信用局检查"
        }'::jsonb,
        '信用审查员记录信用检查结果的弹窗表单',
        v_credit_check_table_id
    ) RETURNING id INTO v_credit_check_form_id;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, sort_order
    ) VALUES
    (v_credit_check_form_id, v_loan_application_table_id, 'PRIMARY', 'READONLY', 1),
    (v_credit_check_form_id, v_credit_check_table_id, 'RELATED', 'EDITABLE', 2);

    RAISE NOTICE '创建表单：信用检查表单 (ID: %)', v_credit_check_form_id;

    -- 3.3 风险评估表单（弹窗表单）
    INSERT INTO dw_form_definitions (
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
            "title": "风险评估与分析"
        }'::jsonb,
        '风险评估员评估贷款风险的弹窗表单',
        v_loan_application_table_id
    ) RETURNING id INTO v_risk_assessment_form_id;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, sort_order
    ) VALUES
    (v_risk_assessment_form_id, v_loan_application_table_id, 'PRIMARY', 'EDITABLE', 1),
    (v_risk_assessment_form_id, v_applicant_info_table_id, 'SUB', 'READONLY', 2),
    (v_risk_assessment_form_id, v_financial_info_table_id, 'SUB', 'READONLY', 3),
    (v_risk_assessment_form_id, v_credit_check_table_id, 'RELATED', 'READONLY', 4);

    RAISE NOTICE '创建表单：风险评估表单 (ID: %)', v_risk_assessment_form_id;

    -- 3.4 审批表单（主表单）
    INSERT INTO dw_form_definitions (
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
        '经理审批贷款申请的表单',
        v_loan_application_table_id
    ) RETURNING id INTO v_approval_form_id;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, sort_order
    ) VALUES
    (v_approval_form_id, v_loan_application_table_id, 'PRIMARY', 'READONLY', 1),
    (v_approval_form_id, v_applicant_info_table_id, 'SUB', 'READONLY', 2),
    (v_approval_form_id, v_financial_info_table_id, 'SUB', 'READONLY', 3),
    (v_approval_form_id, v_collateral_table_id, 'SUB', 'READONLY', 4),
    (v_approval_form_id, v_credit_check_table_id, 'RELATED', 'READONLY', 5),
    (v_approval_form_id, v_approval_history_table_id, 'RELATED', 'EDITABLE', 6);

    RAISE NOTICE '创建表单：审批表单 (ID: %)', v_approval_form_id;

    -- 3.5 放款表单（主表单）
    INSERT INTO dw_form_definitions (
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
        '财务团队处理贷款放款的表单',
        v_loan_application_table_id
    ) RETURNING id INTO v_disbursement_form_id;

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, sort_order
    ) VALUES
    (v_disbursement_form_id, v_loan_application_table_id, 'PRIMARY', 'EDITABLE', 1),
    (v_disbursement_form_id, v_applicant_info_table_id, 'SUB', 'READONLY', 2),
    (v_disbursement_form_id, v_financial_info_table_id, 'SUB', 'READONLY', 3);

    RAISE NOTICE '创建表单：放款表单 (ID: %)', v_disbursement_form_id;

    -- =========================================================================
    -- 第四部分：创建动作定义
    -- =========================================================================
    
    -- 4.1 提交申请（流程提交）
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Submit Application',
        'PROCESS_SUBMIT',
        '提交贷款申请开始审批流程',
        '{
            "requireComment": false,
            "confirmMessage": "确认提交此贷款申请？",
            "successMessage": "贷款申请已成功提交"
        }'::jsonb,
        'Upload',
        'primary'
    );

    -- 4.2 撤回申请
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Withdraw Application',
        'WITHDRAW',
        '撤回已提交的贷款申请',
        '{
            "targetStatus": "WITHDRAWN",
            "requireComment": true,
            "requireReason": true,
            "allowedFromStatus": ["SUBMITTED", "IN_REVIEW", "INFO_REQUIRED"],
            "confirmMessage": "确认撤回此贷款申请？",
            "successMessage": "贷款申请已撤回"
        }'::jsonb,
        'RollbackOutlined',
        'warning'
    );

    -- 4.3 执行信用检查（弹窗表单）
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Perform Credit Check',
        'FORM_POPUP',
        '打开信用检查表单记录信用局结果',
        format('{
            "formId": %s,
            "formName": "Credit Check Form",
            "popupWidth": "800px",
            "popupTitle": "信用局检查",
            "requireComment": false,
            "allowedRoles": ["CREDIT_OFFICER", "RISK_MANAGER"],
            "successMessage": "信用检查结果已保存"
        }', v_credit_check_form_id)::jsonb,
        'FileSearch',
        'info'
    );

    -- 4.4 查看信用报告（弹窗表单，只读）
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'View Credit Report',
        'FORM_POPUP',
        '查看信用检查结果（只读）',
        format('{
            "formId": %s,
            "formName": "Credit Check Form",
            "popupWidth": "800px",
            "popupTitle": "信用报告",
            "readOnly": true,
            "showSubmitButton": false
        }', v_credit_check_form_id)::jsonb,
        'Document',
        'default'
    );

    -- 4.5 风险评估（弹窗表单）
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Assess Risk',
        'FORM_POPUP',
        '对贷款申请进行风险评估',
        format('{
            "formId": %s,
            "formName": "Risk Assessment Form",
            "popupWidth": "900px",
            "popupTitle": "风险评估与分析",
            "requireComment": false,
            "allowedRoles": ["RISK_OFFICER", "RISK_MANAGER"],
            "successMessage": "风险评估已完成"
        }', v_risk_assessment_form_id)::jsonb,
        'Warning',
        'warning'
    );

    -- 4.6 批准
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Approve',
        'APPROVE',
        '批准贷款申请',
        '{
            "targetStatus": "APPROVED",
            "requireComment": true,
            "confirmMessage": "确认批准此贷款申请？",
            "allowedRoles": ["MANAGER", "SENIOR_MANAGER"],
            "successMessage": "贷款申请已批准"
        }'::jsonb,
        'Check',
        'success'
    );

    -- 4.7 拒绝
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Reject',
        'REJECT',
        '拒绝贷款申请',
        '{
            "targetStatus": "REJECTED",
            "requireComment": true,
            "requireReason": true,
            "confirmMessage": "确认拒绝此贷款申请？",
            "successMessage": "贷款申请已拒绝"
        }'::jsonb,
        'Close',
        'danger'
    );

    -- 4.8 请求补充信息（弹窗表单）
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Request Additional Info',
        'FORM_POPUP',
        '向申请人请求补充信息',
        '{
            "formType": "COMMENT_FORM",
            "popupWidth": "600px",
            "popupTitle": "请求补充信息",
            "requireComment": true,
            "commentLabel": "请说明需要补充的信息",
            "targetStatus": "INFO_REQUIRED",
            "notifyApplicant": true,
            "successMessage": "信息请求已发送给申请人"
        }'::jsonb,
        'QuestionCircle',
        'warning'
    );

    -- 4.9 验证文档
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Verify Documents',
        'APPROVE',
        '验证所有上传的文档',
        '{
            "targetStatus": "DOCUMENTS_VERIFIED",
            "requireComment": false,
            "allowedRoles": ["DOCUMENT_VERIFIER", "OPERATIONS"],
            "successMessage": "文档验证成功"
        }'::jsonb,
        'FileDone',
        'success'
    );

    -- 4.10 计算月供（API 调用）
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Calculate EMI',
        'API_CALL',
        '根据贷款金额和期限计算月供',
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
            "successMessage": "月供计算成功"
        }'::jsonb,
        'Calculator',
        'info'
    );

    -- 4.11 处理放款
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Process Disbursement',
        'APPROVE',
        '处理贷款放款到申请人账户',
        '{
            "targetStatus": "DISBURSED",
            "requireComment": true,
            "confirmMessage": "确认处理此贷款的放款？",
            "allowedRoles": ["FINANCE_OFFICER", "FINANCE_MANAGER"],
            "successMessage": "贷款已成功放款"
        }'::jsonb,
        'DollarCircle',
        'success'
    );

    -- 4.12 查询申请（API 调用）
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Query Applications',
        'API_CALL',
        '使用过滤条件查询贷款申请',
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

    -- 4.13 验证账户（API 调用）
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Verify Account',
        'API_CALL',
        '验证申请人的银行账户',
        '{
            "url": "/api/lending/verify-account",
            "method": "POST",
            "parameters": {
                "bankName": "{{bank_name}}",
                "accountNumber": "{{account_number}}",
                "accountType": "{{account_type}}"
            },
            "successMessage": "账户验证成功"
        }'::jsonb,
        'BankOutlined',
        'info'
    );

    -- 4.14 标记为低风险
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Mark as Low Risk',
        'APPROVE',
        '将贷款标记为低风险',
        '{
            "targetStatus": "LOW_RISK",
            "requireComment": false,
            "updateFields": {
                "risk_rating": "Low"
            },
            "successMessage": "已标记为低风险"
        }'::jsonb,
        'CheckCircle',
        'success'
    );

    -- 4.15 标记为高风险
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, description,
        config_json, icon, button_color
    ) VALUES (
        v_function_unit_id,
        'Mark as High Risk',
        'REJECT',
        '将贷款标记为高风险',
        '{
            "targetStatus": "HIGH_RISK",
            "requireComment": true,
            "requireReason": true,
            "updateFields": {
                "risk_rating": "High"
            },
            "successMessage": "已标记为高风险"
        }'::jsonb,
        'WarningOutlined',
        'danger'
    );

    RAISE NOTICE '创建了 15 个动作定义';

    -- =========================================================================
    -- 第五部分：输出摘要信息
    -- =========================================================================
    
    RAISE NOTICE '';
    RAISE NOTICE '========================================';
    RAISE NOTICE '数字贷款系统 V2 创建成功！';
    RAISE NOTICE '========================================';
    RAISE NOTICE '';
    RAISE NOTICE '功能单元 ID: %', v_function_unit_id;
    RAISE NOTICE '';
    RAISE NOTICE '表定义：';
    RAISE NOTICE '  - 贷款申请（主表）: %', v_loan_application_table_id;
    RAISE NOTICE '  - 申请人信息（子表）: %', v_applicant_info_table_id;
    RAISE NOTICE '  - 财务信息（子表）: %', v_financial_info_table_id;
    RAISE NOTICE '  - 抵押物信息（子表）: %', v_collateral_table_id;
    RAISE NOTICE '  - 信用检查结果（关联表）: %', v_credit_check_table_id;
    RAISE NOTICE '  - 审批历史（关联表）: %', v_approval_history_table_id;
    RAISE NOTICE '  - 文档附件（关联表）: %', v_documents_table_id;
    RAISE NOTICE '';
    RAISE NOTICE '表单定义：';
    RAISE NOTICE '  - 贷款申请表单: %', v_application_form_id;
    RAISE NOTICE '  - 信用检查表单（弹窗）: %', v_credit_check_form_id;
    RAISE NOTICE '  - 风险评估表单（弹窗）: %', v_risk_assessment_form_id;
    RAISE NOTICE '  - 审批表单: %', v_approval_form_id;
    RAISE NOTICE '  - 放款表单: %', v_disbursement_form_id;
    RAISE NOTICE '';
    RAISE NOTICE '动作定义：15 个（包括弹窗表单动作和 API 调用）';
    RAISE NOTICE '';
    RAISE NOTICE '下一步：';
    RAISE NOTICE '  1. 运行: 02-insert-bpmn-process.ps1';
    RAISE NOTICE '  2. 运行: 03-bind-actions.sql';
    RAISE NOTICE '  3. 在开发者工作台部署功能单元';
    RAISE NOTICE '  4. 在用户门户测试完整流程';
    RAISE NOTICE '========================================';
    
END $$;
