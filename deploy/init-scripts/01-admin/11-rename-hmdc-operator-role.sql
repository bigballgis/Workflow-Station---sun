-- Align the HASE HMDC operator role with Process Design.
-- 06-hase-organization-seed.sql inserted code HMDC_Operrator_Role (typo).
-- ATM Mark Completed binds assigneeType=BU_ROLE / roleIds=HMDC_Operator_Role;
-- isEligibleRole failed and those tasks stayed unassigned.
-- Same role id so existing sys_business_unit_roles and UBR rows stay valid.

\echo '========================================='
\echo 'Rename HMDC_Operrator_Role -> HMDC_Operator_Role...'
\echo '========================================='

UPDATE sys_roles
SET code = 'HMDC_Operator_Role',
    name = 'HMDC_Operator_Role',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'HMDC_Operrator_Role'
  AND NOT EXISTS (
      SELECT 1 FROM sys_roles r2
      WHERE r2.code = 'HMDC_Operator_Role'
  );

\echo '✓ HMDC operator role code aligned with Process Design'
