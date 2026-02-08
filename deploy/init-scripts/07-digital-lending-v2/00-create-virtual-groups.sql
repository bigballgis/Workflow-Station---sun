-- =============================================================================
-- 创建数字贷款系统所需的虚拟组
-- =============================================================================

DO $$
BEGIN
    -- 文档验证组
    IF NOT EXISTS (SELECT 1 FROM sys_virtual_groups WHERE code = 'DOCUMENT_VERIFIERS') THEN
        INSERT INTO sys_virtual_groups (
            code, name, description, type, status, created_by, created_at
        ) VALUES (
            'DOCUMENT_VERIFIERS',
            '文档验证组',
            '负责验证贷款申请的文档和材料',
            'FUNCTIONAL',
            'ACTIVE',
            'system',
            CURRENT_TIMESTAMP
        );
        RAISE NOTICE '创建虚拟组：文档验证组';
    ELSE
        RAISE NOTICE '虚拟组已存在：文档验证组';
    END IF;

    -- 信用审查组
    IF NOT EXISTS (SELECT 1 FROM sys_virtual_groups WHERE code = 'CREDIT_OFFICERS') THEN
        INSERT INTO sys_virtual_groups (
            code, name, description, type, status, created_by, created_at
        ) VALUES (
            'CREDIT_OFFICERS',
            '信用审查组',
            '负责执行信用检查和评估',
            'FUNCTIONAL',
            'ACTIVE',
            'system',
            CURRENT_TIMESTAMP
        );
        RAISE NOTICE '创建虚拟组：信用审查组';
    ELSE
        RAISE NOTICE '虚拟组已存在：信用审查组';
    END IF;

    -- 风险评估组
    IF NOT EXISTS (SELECT 1 FROM sys_virtual_groups WHERE code = 'RISK_OFFICERS') THEN
        INSERT INTO sys_virtual_groups (
            code, name, description, type, status, created_by, created_at
        ) VALUES (
            'RISK_OFFICERS',
            '风险评估组',
            '负责评估贷款风险等级',
            'FUNCTIONAL',
            'ACTIVE',
            'system',
            CURRENT_TIMESTAMP
        );
        RAISE NOTICE '创建虚拟组：风险评估组';
    ELSE
        RAISE NOTICE '虚拟组已存在：风险评估组';
    END IF;

    -- 财务组
    IF NOT EXISTS (SELECT 1 FROM sys_virtual_groups WHERE code = 'FINANCE_TEAM') THEN
        INSERT INTO sys_virtual_groups (
            code, name, description, type, status, created_by, created_at
        ) VALUES (
            'FINANCE_TEAM',
            '财务组',
            '负责处理贷款放款和财务操作',
            'FUNCTIONAL',
            'ACTIVE',
            'system',
            CURRENT_TIMESTAMP
        );
        RAISE NOTICE '创建虚拟组：财务组';
    ELSE
        RAISE NOTICE '虚拟组已存在：财务组';
    END IF;

    RAISE NOTICE '========================================';
    RAISE NOTICE '虚拟组创建完成！';
    RAISE NOTICE '========================================';
END $$;
