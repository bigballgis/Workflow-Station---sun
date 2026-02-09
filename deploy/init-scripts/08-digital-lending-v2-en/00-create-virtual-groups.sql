-- =============================================================================
-- Create Virtual Groups for Digital Lending System
-- =============================================================================

INSERT INTO sys_virtual_groups (id, code, name, description, type, status, created_by, created_at)
VALUES
('vg-doc-verifiers', 'DOCUMENT_VERIFIERS', 'Document Verifiers', 'Responsible for verifying loan application documents and materials', 'CUSTOM', 'ACTIVE', 'system', CURRENT_TIMESTAMP),
('vg-credit-officers', 'CREDIT_OFFICERS', 'Credit Officers', 'Responsible for performing credit checks and assessments', 'CUSTOM', 'ACTIVE', 'system', CURRENT_TIMESTAMP),
('vg-risk-officers', 'RISK_OFFICERS', 'Risk Officers', 'Responsible for assessing loan risk levels', 'CUSTOM', 'ACTIVE', 'system', CURRENT_TIMESTAMP),
('vg-finance-team', 'FINANCE_TEAM', 'Finance Team', 'Responsible for processing loan disbursements and financial operations', 'CUSTOM', 'ACTIVE', 'system', CURRENT_TIMESTAMP)
ON CONFLICT (code) DO NOTHING;
