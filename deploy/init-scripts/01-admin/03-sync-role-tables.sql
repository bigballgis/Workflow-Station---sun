-- =====================================================
-- Sync Role Assignment Tables
-- =====================================================
-- Purpose: Synchronize data from sys_virtual_group_roles to sys_role_assignments
-- This ensures both tables have consistent data during the transition period.
-- 
-- Background:
-- - sys_virtual_group_roles: Legacy table used by management UI
-- - sys_role_assignments: New unified table used by permission queries
-- 
-- This script should be run AFTER 01-create-roles-and-groups.sql
-- =====================================================

\echo ''
\echo '========================================='
\echo 'Synchronizing Role Assignment Tables...'
\echo '========================================='

-- Migrate data from sys_virtual_group_roles to sys_role_assignments
INSERT INTO sys_role_assignments (id, role_id, target_type, target_id, assigned_by, assigned_at, created_at, updated_at)
SELECT 
    'ra-' || vgr.id as id,
    vgr.role_id,
    'VIRTUAL_GROUP' as target_type,
    vgr.virtual_group_id as target_id,
    COALESCE(vgr.created_by, 'system') as assigned_by,
    COALESCE(vgr.created_at, CURRENT_TIMESTAMP) as assigned_at,
    COALESCE(vgr.created_at, CURRENT_TIMESTAMP) as created_at,
    CURRENT_TIMESTAMP as updated_at
FROM sys_virtual_group_roles vgr
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_assignments ra
    WHERE ra.role_id = vgr.role_id
    AND ra.target_type = 'VIRTUAL_GROUP'
    AND ra.target_id = vgr.virtual_group_id
)
ON CONFLICT (role_id, target_type, target_id) DO NOTHING;

\echo '✓ Data synchronized from sys_virtual_group_roles to sys_role_assignments'

-- Verify data consistency
\echo ''
\echo 'Data Consistency Check:'
\echo '========================================='

SELECT 
    'sys_virtual_group_roles' as source_table,
    COUNT(*) as record_count
FROM sys_virtual_group_roles
UNION ALL
SELECT 
    'sys_role_assignments (VIRTUAL_GROUP)' as source_table,
    COUNT(*) as record_count
FROM sys_role_assignments
WHERE target_type = 'VIRTUAL_GROUP';

\echo ''
\echo 'Detailed Comparison:'
\echo '========================================='

SELECT 
    vg.name as virtual_group,
    r.name as role,
    r.type as role_type,
    CASE 
        WHEN ra.id IS NOT NULL THEN '✓ Synced'
        ELSE '✗ Not Synced'
    END as status
FROM sys_virtual_group_roles vgr
JOIN sys_virtual_groups vg ON vgr.virtual_group_id = vg.id
JOIN sys_roles r ON vgr.role_id = r.id
LEFT JOIN sys_role_assignments ra ON 
    ra.role_id = vgr.role_id 
    AND ra.target_type = 'VIRTUAL_GROUP' 
    AND ra.target_id = vgr.virtual_group_id
ORDER BY vg.name;

\echo ''
\echo '========================================='
\echo 'Synchronization Complete!'
\echo '========================================='
\echo ''
\echo 'Note: Both tables now contain the same role assignment data.'
\echo '- sys_virtual_group_roles: Used by management UI (legacy)'
\echo '- sys_role_assignments: Used by permission queries (recommended)'
\echo ''
