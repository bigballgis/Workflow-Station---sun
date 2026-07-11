-- =============================================================================
-- HASE organization seed (ASP → HK → HASE → hase-hmdc) + HMDC roles
-- Exported from dev DB on 2026-07-06
-- Idempotent: safe to re-run on existing databases
-- =============================================================================
BEGIN;

-- HASE organization tree (ASP → HK → HASE → hase-hmdc)
INSERT INTO sys_business_units (
    id, code, name, parent_id, level, path, sort_order, status, description,
    cost_center, location, phone, created_at, created_by, updated_at, updated_by
)
VALUES
(
    'e30b16fa-874e-435c-9f15-7ba31416678a', 'ASP', 'ASP', NULL, 1, '/e30b16fa-874e-435c-9f15-7ba31416678a', 0, 'ACTIVE', NULL, NULL, NULL, NULL, '2026-07-06 07:29:19.934876+00', NULL, '2026-07-06 07:29:19.934918+00', NULL
),
(
    'e78462f2-86d7-40d9-a128-d5a1af7e0cc4', 'HK', 'HK', 'e30b16fa-874e-435c-9f15-7ba31416678a', 2, '/e30b16fa-874e-435c-9f15-7ba31416678a/e78462f2-86d7-40d9-a128-d5a1af7e0cc4', 0, 'ACTIVE', NULL, NULL, NULL, NULL, '2026-07-06 07:29:42.227857+00', NULL, '2026-07-06 07:29:42.227877+00', NULL
),
(
    '2f577e20-f019-49b3-9f8d-e5fe0e9a11e3', 'HASE', 'HASE', 'e78462f2-86d7-40d9-a128-d5a1af7e0cc4', 3, '/e30b16fa-874e-435c-9f15-7ba31416678a/e78462f2-86d7-40d9-a128-d5a1af7e0cc4/2f577e20-f019-49b3-9f8d-e5fe0e9a11e3', 0, 'ACTIVE', NULL, NULL, NULL, NULL, '2026-07-06 07:30:05.524884+00', NULL, '2026-07-06 07:30:05.524901+00', NULL
),
(
    '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', 'hase-hmdc', 'hase-hmdc', '2f577e20-f019-49b3-9f8d-e5fe0e9a11e3', 4, '/e30b16fa-874e-435c-9f15-7ba31416678a/e78462f2-86d7-40d9-a128-d5a1af7e0cc4/2f577e20-f019-49b3-9f8d-e5fe0e9a11e3/2ca743c1-2af5-4c44-866b-ae8e1ba60acb', 0, 'ACTIVE', NULL, NULL, NULL, NULL, '2026-07-06 07:30:42.280575+00', NULL, '2026-07-06 07:30:42.280593+00', NULL
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    parent_id = EXCLUDED.parent_id,
    level = EXCLUDED.level,
    path = EXCLUDED.path,
    sort_order = EXCLUDED.sort_order,
    status = EXCLUDED.status,
    description = EXCLUDED.description,
    updated_at = EXCLUDED.updated_at;


-- HMDC business roles (BU_BOUNDED)
INSERT INTO sys_roles (id, code, name, type, display_name, status, is_system, created_at, created_by, updated_at, updated_by, lock_version) VALUES ('c5768dc1-94f8-4c39-9ab1-94fead7eee20', 'HMDC_Approver_Role', 'HMDC_Approver_Role', 'BU_BOUNDED', '', 'ACTIVE', false, '2026-07-06 15:32:54.157494', NULL, '2026-07-06 15:32:54.157633', NULL, 0) ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, type = EXCLUDED.type, display_name = EXCLUDED.display_name, status = EXCLUDED.status, updated_at = EXCLUDED.updated_at;
INSERT INTO sys_roles (id, code, name, type, display_name, status, is_system, created_at, created_by, updated_at, updated_by, lock_version) VALUES ('5e40c0fd-7dba-4dd1-9933-eb6cf259a882', 'HMDC_Assign_Role', 'HMDC_Assign_Role', 'BU_BOUNDED', '', 'ACTIVE', false, '2026-07-06 15:32:34.400489', NULL, '2026-07-06 15:32:34.400512', NULL, 0) ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, type = EXCLUDED.type, display_name = EXCLUDED.display_name, status = EXCLUDED.status, updated_at = EXCLUDED.updated_at;
INSERT INTO sys_roles (id, code, name, type, display_name, status, is_system, created_at, created_by, updated_at, updated_by, lock_version) VALUES ('dbfc6328-3095-40f2-9e3a-efd4f55cba05', 'HMDC_Index_Role', 'HMDC_Index_Role', 'BU_BOUNDED', '', 'ACTIVE', false, '2026-07-06 15:32:06.04383', NULL, '2026-07-06 15:32:06.043922', NULL, 0) ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, type = EXCLUDED.type, display_name = EXCLUDED.display_name, status = EXCLUDED.status, updated_at = EXCLUDED.updated_at;
INSERT INTO sys_roles (id, code, name, type, display_name, status, is_system, created_at, created_by, updated_at, updated_by, lock_version) VALUES ('25a60bcb-cd1b-4ef4-856d-93a646fe7998', 'HMDC_Operrator_Role', 'HMDC_Operrator_Role', 'BU_BOUNDED', '', 'ACTIVE', false, '2026-07-06 15:33:16.55783', NULL, '2026-07-06 15:33:16.557871', NULL, 0) ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, type = EXCLUDED.type, display_name = EXCLUDED.display_name, status = EXCLUDED.status, updated_at = EXCLUDED.updated_at;


-- Bind HMDC roles to hase-hmdc business unit
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by) VALUES ('6ba75b79-7b41-4d44-b22d-3af9ebad03f1', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', '25a60bcb-cd1b-4ef4-856d-93a646fe7998', '2026-07-06 15:33:30.523382', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by) VALUES ('210f70c2-3e3f-4beb-9749-8067d0c10bac', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', '5e40c0fd-7dba-4dd1-9933-eb6cf259a882', '2026-07-06 15:33:27.290339', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by) VALUES ('5e4a60b1-f687-4073-9812-e8caf49232f7', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', 'c5768dc1-94f8-4c39-9ab1-94fead7eee20', '2026-07-06 15:33:25.332937', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_business_unit_roles (id, business_unit_id, role_id, created_at, created_by) VALUES ('f62c7d22-0c61-4b68-95be-7f2755edd12d', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', 'dbfc6328-3095-40f2-9e3a-efd4f55cba05', '2026-07-06 15:33:28.953162', NULL) ON CONFLICT (id) DO NOTHING;


-- User ↔ hase-hmdc business unit memberships
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by) VALUES ('4b52f287-5958-4bd8-b9ee-59628691fc9a', 'user-admin', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', '2026-07-06 15:31:33.745389', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by) VALUES ('1b0fcb4c-4957-4379-80ab-24de7113f42b', 'user-dev', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', '2026-07-06 15:31:07.097779', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by) VALUES ('c08ca745-fe9f-445d-b509-ee914e0c812b', 'user-e2e-lina', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', '2026-07-06 15:31:10.380916', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by) VALUES ('285f554a-41e0-4bd6-a8d7-e28c35486db7', 'user-e2e-sunqiang', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', '2026-07-06 15:31:15.784129', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by) VALUES ('c980ef68-6ed1-4bf9-9cfa-46497d0bbefc', 'user-e2e-wangfang', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', '2026-07-06 15:31:12.616464', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by) VALUES ('96714a36-f9d9-4ab8-a936-d7fd781c44d0', 'user-e2e-wugang', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', '2026-07-06 15:31:17.957401', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by) VALUES ('4ec8dcb8-f066-4a32-9a13-696aecfe9bf8', 'user-e2e-zhangwei', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', '2026-07-06 15:31:25.554826', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by) VALUES ('6024bbb2-6597-421b-9b73-5a0295c22d56', 'user-e2e-zhaomin', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', '2026-07-06 15:31:19.959871', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by) VALUES ('b4654c71-66c6-4a7a-952e-c5db1cba384d', 'user-e2e-zhoujie', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', '2026-07-06 15:31:27.521359', NULL) ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_user_business_units (id, user_id, business_unit_id, created_at, created_by) VALUES ('c5ea886e-4145-40d3-9883-2a33ccb2cb68', 'user-test-44027893', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', '2026-07-06 15:31:23.245157', NULL) ON CONFLICT (id) DO NOTHING;


-- User ↔ role on hase-hmdc (portal New Requests / task assignee demo)
INSERT INTO sys_user_business_unit_roles (id, user_id, business_unit_id, role_id, created_at, created_by) VALUES ('c102ccd3-5027-40d9-8f98-2555c2ae335b', 'user-dev', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', 'dbfc6328-3095-40f2-9e3a-efd4f55cba05', '2026-07-06 15:37:13.018296', 'user-dev') ON CONFLICT (id) DO NOTHING;
INSERT INTO sys_user_business_unit_roles (id, user_id, business_unit_id, role_id, created_at, created_by) VALUES ('bfb924e9-a15d-4879-8f0b-916e7fe95efe', 'user-e2e-lina', '2ca743c1-2af5-4c44-866b-ae8e1ba60acb', '5e40c0fd-7dba-4dd1-9933-eb6cf259a882', '2026-07-06 16:12:57.461103', 'user-dev') ON CONFLICT (id) DO NOTHING;


COMMIT;
