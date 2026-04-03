-- =====================================================
-- 数字信贷 V2：将 E2E 用户绑定到 BPMN 虚拟组
-- =====================================================
-- 依赖: 01-admin/05-e2e-test-users-and-business-units.sql
--       本目录 00-create-virtual-groups.sql
-- 对应 BPMN assignee: DOCUMENT_VERIFIERS / CREDIT_OFFICERS / RISK_OFFICERS / FINANCE_TEAM
-- =====================================================

INSERT INTO sys_virtual_group_members (id, group_id, user_id, joined_at, added_by)
VALUES
('vgm-e2e-doc', 'vg-doc-verifiers',  'user-e2e-zhaomin', CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-crd', 'vg-credit-officers', 'user-e2e-sunqiang', CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-rsk', 'vg-risk-officers',   'user-e2e-zhoujie', CURRENT_TIMESTAMP, 'system'),
('vgm-e2e-fin', 'vg-finance-team',    'user-e2e-wugang', CURRENT_TIMESTAMP, 'system')
ON CONFLICT (group_id, user_id) DO NOTHING;
