-- 添加 approval_order 列到 sys_function_unit_approvals 表
-- 用于支持多级审批流程中的审批顺序

-- 添加列（如果不存在）
ALTER TABLE sys_function_unit_approvals 
ADD COLUMN IF NOT EXISTS approval_order INTEGER DEFAULT 1;

-- 修改 approver_id 为可空（审批创建时可能还没有指定审批人）
ALTER TABLE sys_function_unit_approvals 
ALTER COLUMN approver_id DROP NOT NULL;

-- 添加注释
COMMENT ON COLUMN sys_function_unit_approvals.approval_order IS '审批顺序，用于多级审批流程';
COMMENT ON COLUMN sys_function_unit_approvals.approver_id IS '审批人ID，创建时可为空，审批时必须指定';

-- 为现有记录设置默认值
UPDATE sys_function_unit_approvals 
SET approval_order = 1 
WHERE approval_order IS NULL;
