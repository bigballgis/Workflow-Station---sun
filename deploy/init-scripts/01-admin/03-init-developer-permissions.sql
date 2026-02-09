-- =====================================================
-- Developer Role Permissions Initialization
-- =====================================================
-- This script initializes permissions for developer roles
-- =====================================================

-- Insert permissions for DEVELOPER role
-- DEVELOPER can: EDIT, DEPLOY, PUBLISH existing function units
-- DEVELOPER cannot: CREATE or DELETE function units
-- DEVELOPER can: CREATE/EDIT components within function units (forms, processes, tables, actions)
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
VALUES 
    -- Function Unit permissions (no CREATE, no DELETE)
    ('drp-dev-002', 'role-developer', 'FUNCTION_UNIT_VIEW', CURRENT_TIMESTAMP),
    ('drp-dev-003', 'role-developer', 'FUNCTION_UNIT_UPDATE', CURRENT_TIMESTAMP),
    ('drp-dev-005', 'role-developer', 'FUNCTION_UNIT_DEVELOP', CURRENT_TIMESTAMP),
    ('drp-dev-006', 'role-developer', 'FUNCTION_UNIT_PUBLISH', CURRENT_TIMESTAMP),
    -- Form permissions (can create/edit within function units, no delete)
    ('drp-dev-007', 'role-developer', 'FORM_CREATE', CURRENT_TIMESTAMP),
    ('drp-dev-008', 'role-developer', 'FORM_VIEW', CURRENT_TIMESTAMP),
    ('drp-dev-009', 'role-developer', 'FORM_UPDATE', CURRENT_TIMESTAMP),
    -- Process permissions (can create/edit within function units, no delete)
    ('drp-dev-011', 'role-developer', 'PROCESS_CREATE', CURRENT_TIMESTAMP),
    ('drp-dev-012', 'role-developer', 'PROCESS_VIEW', CURRENT_TIMESTAMP),
    ('drp-dev-013', 'role-developer', 'PROCESS_UPDATE', CURRENT_TIMESTAMP),
    -- Table permissions (can create/edit within function units, no delete)
    ('drp-dev-015', 'role-developer', 'TABLE_CREATE', CURRENT_TIMESTAMP),
    ('drp-dev-016', 'role-developer', 'TABLE_VIEW', CURRENT_TIMESTAMP),
    ('drp-dev-017', 'role-developer', 'TABLE_UPDATE', CURRENT_TIMESTAMP),
    -- Action permissions (can create/edit within function units, no delete)
    ('drp-dev-019', 'role-developer', 'ACTION_CREATE', CURRENT_TIMESTAMP),
    ('drp-dev-020', 'role-developer', 'ACTION_VIEW', CURRENT_TIMESTAMP),
    ('drp-dev-021', 'role-developer', 'ACTION_UPDATE', CURRENT_TIMESTAMP)
ON CONFLICT (role_id, permission) DO NOTHING;

-- Insert permissions for TEAM_LEAD role
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
VALUES 
    ('drp-tl-001', 'role-team-lead', 'FUNCTION_UNIT_CREATE', CURRENT_TIMESTAMP),
    ('drp-tl-002', 'role-team-lead', 'FUNCTION_UNIT_VIEW', CURRENT_TIMESTAMP),
    ('drp-tl-003', 'role-team-lead', 'FUNCTION_UNIT_UPDATE', CURRENT_TIMESTAMP),
    ('drp-tl-004', 'role-team-lead', 'FUNCTION_UNIT_DEVELOP', CURRENT_TIMESTAMP),
    ('drp-tl-005', 'role-team-lead', 'FUNCTION_UNIT_PUBLISH', CURRENT_TIMESTAMP),
    ('drp-tl-006', 'role-team-lead', 'FORM_CREATE', CURRENT_TIMESTAMP),
    ('drp-tl-007', 'role-team-lead', 'FORM_VIEW', CURRENT_TIMESTAMP),
    ('drp-tl-008', 'role-team-lead', 'FORM_UPDATE', CURRENT_TIMESTAMP),
    ('drp-tl-009', 'role-team-lead', 'PROCESS_CREATE', CURRENT_TIMESTAMP),
    ('drp-tl-010', 'role-team-lead', 'PROCESS_VIEW', CURRENT_TIMESTAMP),
    ('drp-tl-011', 'role-team-lead', 'PROCESS_UPDATE', CURRENT_TIMESTAMP),
    ('drp-tl-012', 'role-team-lead', 'TABLE_CREATE', CURRENT_TIMESTAMP),
    ('drp-tl-013', 'role-team-lead', 'TABLE_VIEW', CURRENT_TIMESTAMP),
    ('drp-tl-014', 'role-team-lead', 'TABLE_UPDATE', CURRENT_TIMESTAMP),
    ('drp-tl-015', 'role-team-lead', 'ACTION_CREATE', CURRENT_TIMESTAMP),
    ('drp-tl-016', 'role-team-lead', 'ACTION_VIEW', CURRENT_TIMESTAMP),
    ('drp-tl-017', 'role-team-lead', 'ACTION_UPDATE', CURRENT_TIMESTAMP)
ON CONFLICT (role_id, permission) DO NOTHING;

-- Insert permissions for TECH_LEAD role (all permissions)
INSERT INTO sys_developer_role_permissions (id, role_id, permission, created_at)
VALUES 
    ('drp-tech-001', 'role-tech-lead', 'FUNCTION_UNIT_CREATE', CURRENT_TIMESTAMP),
    ('drp-tech-002', 'role-tech-lead', 'FUNCTION_UNIT_VIEW', CURRENT_TIMESTAMP),
    ('drp-tech-003', 'role-tech-lead', 'FUNCTION_UNIT_UPDATE', CURRENT_TIMESTAMP),
    ('drp-tech-004', 'role-tech-lead', 'FUNCTION_UNIT_DELETE', CURRENT_TIMESTAMP),
    ('drp-tech-005', 'role-tech-lead', 'FUNCTION_UNIT_DEVELOP', CURRENT_TIMESTAMP),
    ('drp-tech-006', 'role-tech-lead', 'FUNCTION_UNIT_PUBLISH', CURRENT_TIMESTAMP),
    ('drp-tech-007', 'role-tech-lead', 'FORM_CREATE', CURRENT_TIMESTAMP),
    ('drp-tech-008', 'role-tech-lead', 'FORM_VIEW', CURRENT_TIMESTAMP),
    ('drp-tech-009', 'role-tech-lead', 'FORM_UPDATE', CURRENT_TIMESTAMP),
    ('drp-tech-010', 'role-tech-lead', 'FORM_DELETE', CURRENT_TIMESTAMP),
    ('drp-tech-011', 'role-tech-lead', 'PROCESS_CREATE', CURRENT_TIMESTAMP),
    ('drp-tech-012', 'role-tech-lead', 'PROCESS_VIEW', CURRENT_TIMESTAMP),
    ('drp-tech-013', 'role-tech-lead', 'PROCESS_UPDATE', CURRENT_TIMESTAMP),
    ('drp-tech-014', 'role-tech-lead', 'PROCESS_DELETE', CURRENT_TIMESTAMP),
    ('drp-tech-015', 'role-tech-lead', 'TABLE_CREATE', CURRENT_TIMESTAMP),
    ('drp-tech-016', 'role-tech-lead', 'TABLE_VIEW', CURRENT_TIMESTAMP),
    ('drp-tech-017', 'role-tech-lead', 'TABLE_UPDATE', CURRENT_TIMESTAMP),
    ('drp-tech-018', 'role-tech-lead', 'TABLE_DELETE', CURRENT_TIMESTAMP),
    ('drp-tech-019', 'role-tech-lead', 'ACTION_CREATE', CURRENT_TIMESTAMP),
    ('drp-tech-020', 'role-tech-lead', 'ACTION_VIEW', CURRENT_TIMESTAMP),
    ('drp-tech-021', 'role-tech-lead', 'ACTION_UPDATE', CURRENT_TIMESTAMP),
    ('drp-tech-022', 'role-tech-lead', 'ACTION_DELETE', CURRENT_TIMESTAMP)
ON CONFLICT (role_id, permission) DO NOTHING;
