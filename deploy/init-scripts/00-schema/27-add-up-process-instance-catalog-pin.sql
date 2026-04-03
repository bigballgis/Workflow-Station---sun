-- 与 user-portal Flyway V404 对齐：发起流程时钉死功能单元目录版本
ALTER TABLE up_process_instance ADD COLUMN IF NOT EXISTS function_unit_catalog_id VARCHAR(64);
ALTER TABLE up_process_instance ADD COLUMN IF NOT EXISTS function_unit_code VARCHAR(50);
ALTER TABLE up_process_instance ADD COLUMN IF NOT EXISTS function_unit_version_label VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_up_pi_fu_catalog ON up_process_instance(function_unit_catalog_id);
CREATE INDEX IF NOT EXISTS idx_up_pi_fu_code_ver ON up_process_instance(function_unit_code, function_unit_version_label);
