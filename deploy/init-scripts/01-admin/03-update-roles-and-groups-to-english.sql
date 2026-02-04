-- Update roles and virtual groups names and descriptions to English
-- Date: 2026-02-04

-- Update sys_roles to English
UPDATE sys_roles SET 
    name = 'System Administrator',
    description = 'System administrator with full access to all system functions'
WHERE code = 'SYS_ADMIN';

UPDATE sys_roles SET 
    name = 'Auditor',
    description = 'System auditor with read-only access to audit logs and system monitoring'
WHERE code = 'AUDITOR';

UPDATE sys_roles SET 
    name = 'Workflow Designer',
    description = 'Workflow designer with access to process and form design tools'
WHERE code = 'DESIGNER';

UPDATE sys_roles SET 
    name = 'Workflow Developer',
    description = 'Workflow developer with access to developer workstation'
WHERE code = 'DEVELOPER';

UPDATE sys_roles SET 
    name = 'Department Manager',
    description = 'Department manager with access to team workflows and approvals'
WHERE code = 'MANAGER';

-- Update sys_virtual_groups to English
UPDATE sys_virtual_groups SET 
    name = 'System Administrators',
    description = 'Virtual group for system administrators with full system access'
WHERE code = 'SYSTEM_ADMINISTRATORS';

UPDATE sys_virtual_groups SET 
    name = 'Auditors',
    description = 'Virtual group for system auditors with monitoring and audit access'
WHERE code = 'AUDITORS';

UPDATE sys_virtual_groups SET 
    name = 'Workflow Designers',
    description = 'Virtual group for workflow designers'
WHERE code = 'DESIGNERS';

UPDATE sys_virtual_groups SET 
    name = 'Workflow Developers',
    description = 'Virtual group for workflow developers'
WHERE code = 'DEVELOPERS';

UPDATE sys_virtual_groups SET 
    name = 'Department Managers',
    description = 'Virtual group for department managers'
WHERE code = 'MANAGERS';
