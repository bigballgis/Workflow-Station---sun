-- =====================================================
-- TEST DATA: Department Managers
-- Sets manager_id and secondary_manager_id for departments
-- =====================================================

-- Corporate Banking Department - Director as manager
UPDATE sys_departments 
SET manager_id = 'corp-director-001',
    secondary_manager_id = 'corp-manager-001',
    updated_at = NOW()
WHERE id = 'DEPT-CORP-BANKING';

-- HR Department - HR Manager as manager
UPDATE sys_departments 
SET manager_id = 'hr-manager-001',
    updated_at = NOW()
WHERE id = 'DEPT-HR';

-- IT Department - Technical Director as manager
UPDATE sys_departments 
SET manager_id = 'tech-director-001',
    updated_at = NOW()
WHERE id = 'DEPT-IT';

-- Core Development Team - Core Lead as manager
UPDATE sys_departments 
SET manager_id = 'core-lead-001',
    updated_at = NOW()
WHERE id = 'DEPT-DEV-CORE';

-- Channel Development Team - Channel Lead as manager
UPDATE sys_departments 
SET manager_id = 'channel-lead-001',
    updated_at = NOW()
WHERE id = 'DEPT-DEV-CHANNEL';

-- Risk Development Team - Risk Lead as manager
UPDATE sys_departments 
SET manager_id = 'risk-lead-001',
    updated_at = NOW()
WHERE id = 'DEPT-DEV-RISK';

-- Corporate Banking Sub-departments - use corp-manager-001 as manager
UPDATE sys_departments 
SET manager_id = 'corp-manager-001',
    updated_at = NOW()
WHERE id IN ('DEPT-CORP-CLIENT', 'DEPT-CORP-CREDIT', 'DEPT-TRADE-FINANCE', 'DEPT-CASH-MGMT', 'DEPT-TRANSACTION');
