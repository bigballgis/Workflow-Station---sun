-- =====================================================
-- Business roles the Digital Lending demo's actions allow (user decision 2026-11-06, WP19, out-of-scope row 81)
-- =====================================================
-- 08-digital-lending-v2-en/01 restricts five actions by role code, but since its first commit (332e4a0e7) no seed
-- created these roles, so the actions were visible to nobody (Portal shows an action only when the session's role
-- code is in its allowedRoles; precheck P-10 listed them). This creates the roles as BU-bound roles (the only kind
-- left after D-64), makes them eligible in 共享财务中心 (bu-e2e-finance) and gives each restricted action at least one
-- E2E account that can see it - the member of the task's group in 08-digital-lending-v2-en/05:
--   Verify Documents      DOCUMENT_VERIFIER | OPERATIONS     e2e_zhaomin  (Document Verifiers)   DOCUMENT_VERIFIER
--   Perform Credit Check  CREDIT_OFFICER | RISK_MANAGER     e2e_sunqiang (Credit Officers)      CREDIT_OFFICER
--   Assess Risk           RISK_OFFICER | RISK_MANAGER       e2e_zhoujie  (Risk Officers)        RISK_OFFICER
--   Process Disbursement  FINANCE_OFFICER | FINANCE_MANAGER e2e_wugang   (Finance Team)         FINANCE_OFFICER
--   Approve               MANAGER | SENIOR_MANAGER          e2e_lina already holds MANAGER (05)
-- The other codes (OPERATIONS, RISK_MANAGER, FINANCE_MANAGER, SENIOR_MANAGER) exist and are eligible in the same
-- business unit, held by nobody yet. In the Portal an account sees these actions in the 共享财务中心 workspace of
-- that role. Runs after 05 (users, business units) and before the access model switch. Idempotent.
-- Existing databases: run once by hand (the roles are not created by the services):
--   docker exec -i platform-postgres-dev psql -v ON_ERROR_STOP=1 -U <user> -d <db> \
--     -f /docker-entrypoint-initdb.d/01-admin/13-digital-lending-action-roles.sql
-- =====================================================

INSERT INTO sys_roles (id, code, name, type, display_name, status, is_system, created_at, updated_at)
VALUES
    ('role-document-verifier', 'DOCUMENT_VERIFIER', 'Document Verifier', 'BU_BOUNDED',
     'Digital Lending: verifies loan application documents', 'ACTIVE', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('role-operations', 'OPERATIONS', 'Operations', 'BU_BOUNDED',
     'Digital Lending: operations staff who may also verify documents', 'ACTIVE', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('role-credit-officer', 'CREDIT_OFFICER', 'Credit Officer', 'BU_BOUNDED',
     'Digital Lending: performs credit bureau checks', 'ACTIVE', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('role-risk-officer', 'RISK_OFFICER', 'Risk Officer', 'BU_BOUNDED',
     'Digital Lending: assesses loan risk', 'ACTIVE', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('role-risk-manager', 'RISK_MANAGER', 'Risk Manager', 'BU_BOUNDED',
     'Digital Lending: may perform credit checks and risk assessments', 'ACTIVE', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('role-senior-manager', 'SENIOR_MANAGER', 'Senior Manager', 'BU_BOUNDED',
     'Digital Lending: may approve loan applications', 'ACTIVE', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('role-finance-officer', 'FINANCE_OFFICER', 'Finance Officer', 'BU_BOUNDED',
     'Digital Lending: processes loan disbursements', 'ACTIVE', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('role-finance-manager', 'FINANCE_MANAGER', 'Finance Manager', 'BU_BOUNDED',
     'Digital Lending: may process loan disbursements', 'ACTIVE', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;

-- Eligible in 共享财务中心, by code: a database where a role of that code already existed keeps its id.
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by)
SELECT 'bur-e2e-fin-' || lower(replace(r.code, '_', '-')), 'bu-e2e-finance', r.id, CURRENT_TIMESTAMP, 'system'
FROM sys_roles r
WHERE r.code IN ('DOCUMENT_VERIFIER', 'OPERATIONS', 'CREDIT_OFFICER', 'RISK_OFFICER', 'RISK_MANAGER',
                 'SENIOR_MANAGER', 'FINANCE_OFFICER', 'FINANCE_MANAGER')
  AND EXISTS (SELECT 1 FROM sys_business_units bu WHERE bu.id = 'bu-e2e-finance')
ON CONFLICT (business_unit_id, role_id) DO NOTHING;

INSERT INTO sys_user_business_unit_roles (id, user_id, business_unit_id, role_id, created_at, created_by)
SELECT 'ubur-e2e-' || a.username || '-' || lower(replace(a.role_code, '_', '-')), u.id, 'bu-e2e-finance', r.id,
       CURRENT_TIMESTAMP, 'system'
FROM (VALUES ('zhaomin', 'DOCUMENT_VERIFIER'),
             ('sunqiang', 'CREDIT_OFFICER'),
             ('zhoujie', 'RISK_OFFICER'),
             ('wugang', 'FINANCE_OFFICER')) AS a(username, role_code)
JOIN sys_users u ON u.username = 'e2e_' || a.username
JOIN sys_roles r ON r.code = a.role_code
JOIN sys_business_unit_roles bur ON bur.business_unit_id = 'bu-e2e-finance' AND bur.role_id = r.id
JOIN sys_user_business_units ubu ON ubu.user_id = u.id AND ubu.business_unit_id = 'bu-e2e-finance'
ON CONFLICT (user_id, business_unit_id, role_id) DO NOTHING;
