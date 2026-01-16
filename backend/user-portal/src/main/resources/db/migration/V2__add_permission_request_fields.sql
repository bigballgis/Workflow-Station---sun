-- 添加权限申请表的新字段

-- 角色申请相关字段
ALTER TABLE up_permission_request ADD COLUMN IF NOT EXISTS role_id VARCHAR(64);
ALTER TABLE up_permission_request ADD COLUMN IF NOT EXISTS role_name VARCHAR(100);
ALTER TABLE up_permission_request ADD COLUMN IF NOT EXISTS organization_unit_id VARCHAR(64);
ALTER TABLE up_permission_request ADD COLUMN IF NOT EXISTS organization_unit_name VARCHAR(200);

-- 虚拟组申请相关字段
ALTER TABLE up_permission_request ADD COLUMN IF NOT EXISTS virtual_group_id VARCHAR(64);
ALTER TABLE up_permission_request ADD COLUMN IF NOT EXISTS virtual_group_name VARCHAR(200);

-- 业务单元申请相关字段
ALTER TABLE up_permission_request ADD COLUMN IF NOT EXISTS business_unit_id VARCHAR(64);
ALTER TABLE up_permission_request ADD COLUMN IF NOT EXISTS business_unit_name VARCHAR(200);

-- 修改 request_type 字段长度以支持新类型
ALTER TABLE up_permission_request ALTER COLUMN request_type TYPE VARCHAR(30);

-- 修改 permissions 字段为可空（新类型不需要此字段）
ALTER TABLE up_permission_request ALTER COLUMN permissions DROP NOT NULL;
