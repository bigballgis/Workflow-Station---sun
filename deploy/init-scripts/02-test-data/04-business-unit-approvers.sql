-- =====================================================
-- TEST DATA: Business Unit Approvers
-- Sets approvers for business units using sys_approvers table
-- Replaces the old department managers (manager_id/secondary_manager_id)
-- =====================================================

-- Clear existing business unit approvers
DELETE FROM sys_approvers WHERE target_type = 'BUSINESS_UNIT';

-- Corporate Banking Department - Director and Manager as approvers
INSERT INTO sys_approvers (id, target_type, target_id, user_id, created_at, created_by)
VALUES 
    ('approver-corp-1', 'BUSINESS_UNIT', 'DEPT-CORP-BANKING', 'corp-director-001', NOW(), 'system'),
    ('approver-corp-2', 'BUSINESS_UNIT', 'DEPT-CORP-BANKING', 'corp-manager-001', NOW(), 'system')
ON CONFLICT (target_type, target_id, user_id) DO NOTHING;

-- HR Department - HR Manager as approver
INSERT INTO sys_approvers (id, target_type, target_id, user_id, created_at, created_by)
VALUES 
    ('approver-hr-1', 'BUSINESS_UNIT', 'DEPT-HR', 'hr-manager-001', NOW(), 'system')
ON CONFLICT (target_type, target_id, user_id) DO NOTHING;

-- IT Department - Technical Director as approver
INSERT INTO sys_approvers (id, target_type, target_id, user_id, created_at, created_by)
VALUES 
    ('approver-it-1', 'BUSINESS_UNIT', 'DEPT-IT', 'tech-director-001', NOW(), 'system')
ON CONFLICT (target_type, target_id, user_id) DO NOTHING;

-- Core Development Team - Core Lead as approver
INSERT INTO sys_approvers (id, target_type, target_id, user_id, created_at, created_by)
VALUES 
    ('approver-core-1', 'BUSINESS_UNIT', 'DEPT-DEV-CORE', 'core-lead-001', NOW(), 'system')
ON CONFLICT (target_type, target_id, user_id) DO NOTHING;

-- Channel Development Team - Channel Lead as approver
INSERT INTO sys_approvers (id, target_type, target_id, user_id, created_at, created_by)
VALUES 
    ('approver-channel-1', 'BUSINESS_UNIT', 'DEPT-DEV-CHANNEL', 'channel-lead-001', NOW(), 'system')
ON CONFLICT (target_type, target_id, user_id) DO NOTHING;

-- Risk Development Team - Risk Lead as approver
INSERT INTO sys_approvers (id, target_type, target_id, user_id, created_at, created_by)
VALUES 
    ('approver-risk-1', 'BUSINESS_UNIT', 'DEPT-DEV-RISK', 'risk-lead-001', NOW(), 'system')
ON CONFLICT (target_type, target_id, user_id) DO NOTHING;

-- Corporate Banking Sub-departments - Corp Manager as approver
INSERT INTO sys_approvers (id, target_type, target_id, user_id, created_at, created_by)
VALUES 
    ('approver-corp-client-1', 'BUSINESS_UNIT', 'DEPT-CORP-CLIENT', 'corp-manager-001', NOW(), 'system'),
    ('approver-corp-credit-1', 'BUSINESS_UNIT', 'DEPT-CORP-CREDIT', 'corp-manager-001', NOW(), 'system'),
    ('approver-trade-finance-1', 'BUSINESS_UNIT', 'DEPT-TRADE-FINANCE', 'corp-manager-001', NOW(), 'system'),
    ('approver-cash-mgmt-1', 'BUSINESS_UNIT', 'DEPT-CASH-MGMT', 'corp-manager-001', NOW(), 'system'),
    ('approver-transaction-1', 'BUSINESS_UNIT', 'DEPT-TRANSACTION', 'corp-manager-001', NOW(), 'system')
ON CONFLICT (target_type, target_id, user_id) DO NOTHING;
