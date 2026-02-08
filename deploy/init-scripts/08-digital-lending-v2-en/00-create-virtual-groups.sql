-- =============================================================================
-- Create Virtual Groups for Digital Lending System
-- =============================================================================

DO $$
BEGIN
    -- Document Verification Group
    IF NOT EXISTS (SELECT 1 FROM sys_virtual_groups WHERE code = 'DOCUMENT_VERIFIERS') THEN
        INSERT INTO sys_virtual_groups (
            code, name, description, type, status, created_by, created_at
        ) VALUES (
            'DOCUMENT_VERIFIERS',
            'Document Verifiers',
            'Responsible for verifying loan application documents and materials',
            'FUNCTIONAL',
            'ACTIVE',
            'system',
            CURRENT_TIMESTAMP
        );
        RAISE NOTICE 'Created virtual group: Document Verifiers';
    ELSE
        RAISE NOTICE 'Virtual group already exists: Document Verifiers';
    END IF;

    -- Credit Review Group
    IF NOT EXISTS (SELECT 1 FROM sys_virtual_groups WHERE code = 'CREDIT_OFFICERS') THEN
        INSERT INTO sys_virtual_groups (
            code, name, description, type, status, created_by, created_at
        ) VALUES (
            'CREDIT_OFFICERS',
            'Credit Officers',
            'Responsible for performing credit checks and assessments',
            'FUNCTIONAL',
            'ACTIVE',
            'system',
            CURRENT_TIMESTAMP
        );
        RAISE NOTICE 'Created virtual group: Credit Officers';
    ELSE
        RAISE NOTICE 'Virtual group already exists: Credit Officers';
    END IF;

    -- Risk Assessment Group
    IF NOT EXISTS (SELECT 1 FROM sys_virtual_groups WHERE code = 'RISK_OFFICERS') THEN
        INSERT INTO sys_virtual_groups (
            code, name, description, type, status, created_by, created_at
        ) VALUES (
            'RISK_OFFICERS',
            'Risk Officers',
            'Responsible for assessing loan risk levels',
            'FUNCTIONAL',
            'ACTIVE',
            'system',
            CURRENT_TIMESTAMP
        );
        RAISE NOTICE 'Created virtual group: Risk Officers';
    ELSE
        RAISE NOTICE 'Virtual group already exists: Risk Officers';
    END IF;

    -- Finance Team
    IF NOT EXISTS (SELECT 1 FROM sys_virtual_groups WHERE code = 'FINANCE_TEAM') THEN
        INSERT INTO sys_virtual_groups (
            code, name, description, type, status, created_by, created_at
        ) VALUES (
            'FINANCE_TEAM',
            'Finance Team',
            'Responsible for processing loan disbursements and financial operations',
            'FUNCTIONAL',
            'ACTIVE',
            'system',
            CURRENT_TIMESTAMP
        );
        RAISE NOTICE 'Created virtual group: Finance Team';
    ELSE
        RAISE NOTICE 'Virtual group already exists: Finance Team';
    END IF;

    RAISE NOTICE '========================================';
    RAISE NOTICE 'Virtual group creation completed!';
    RAISE NOTICE '========================================';
END $$;
