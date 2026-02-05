-- =============================================================================
-- Digital Lending System - Create Virtual Groups
-- Creates the virtual groups needed for task assignment
-- =============================================================================

-- Insert virtual groups for Digital Lending workflow
INSERT INTO sys_virtual_groups (id, name, code, type, description, status, created_at, updated_at)
VALUES
    (gen_random_uuid()::varchar, 'Document Verifiers', 'DOCUMENT_VERIFIERS', 'CUSTOM', 'Team responsible for verifying loan application documents', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid()::varchar, 'Credit Officers', 'CREDIT_OFFICERS', 'CUSTOM', 'Team responsible for performing credit checks', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid()::varchar, 'Risk Officers', 'RISK_OFFICERS', 'CUSTOM', 'Team responsible for risk assessment', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (gen_random_uuid()::varchar, 'Finance Team', 'FINANCE_TEAM', 'CUSTOM', 'Team responsible for loan disbursement', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- Add the current user (manager) to all virtual groups for testing
-- This allows the manager to handle all tasks
INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at)
SELECT 
    gen_random_uuid()::varchar,
    vg.id,
    u.id,
    CURRENT_TIMESTAMP
FROM sys_virtual_groups vg
CROSS JOIN sys_users u
WHERE vg.code IN ('DOCUMENT_VERIFIERS', 'CREDIT_OFFICERS', 'RISK_OFFICERS', 'FINANCE_TEAM')
AND u.username = 'manager'
AND NOT EXISTS (
    SELECT 1 FROM sys_virtual_group_members vgm
    WHERE vgm.group_id = vg.id AND vgm.user_id = u.id
);

-- Verify virtual groups were created
SELECT 
    vg.name,
    vg.code,
    vg.type,
    COUNT(vgm.id) as member_count
FROM sys_virtual_groups vg
LEFT JOIN sys_virtual_group_members vgm ON vg.id = vgm.group_id
WHERE vg.code IN ('DOCUMENT_VERIFIERS', 'CREDIT_OFFICERS', 'RISK_OFFICERS', 'FINANCE_TEAM')
GROUP BY vg.id, vg.name, vg.code, vg.type
ORDER BY vg.name;
