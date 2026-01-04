-- =====================================================
-- 扩展任务信息表创建脚本
-- 版本: V1.2
-- 描述: 创建支持多维度任务分配的扩展任务信息表
-- 作者: Workflow Engine Team
-- 创建时间: 2026-01-03
-- =====================================================

-- 创建扩展任务信息表
CREATE TABLE wf_extended_task_info (
    -- 主键ID
    id BIGSERIAL PRIMARY KEY,
    
    -- Flowable任务ID（关联到ACT_RU_TASK表）
    task_id VARCHAR(64) NOT NULL UNIQUE,
    
    -- 流程实例ID
    process_instance_id VARCHAR(64) NOT NULL,
    
    -- 流程定义ID
    process_definition_id VARCHAR(64) NOT NULL,
    
    -- 任务定义键
    task_definition_key VARCHAR(255),
    
    -- 任务名称
    task_name VARCHAR(255),
    
    -- 任务描述
    task_description TEXT,
    
    -- 任务分配类型：USER(用户), VIRTUAL_GROUP(虚拟组), DEPT_ROLE(部门角色)
    assignment_type VARCHAR(20) NOT NULL DEFAULT 'USER',
    
    -- 分配目标
    -- 当assignment_type为USER时，存储用户ID
    -- 当assignment_type为VIRTUAL_GROUP时，存储虚拟组ID
    -- 当assignment_type为DEPT_ROLE时，存储"部门ID:角色ID"格式
    assignment_target VARCHAR(255) NOT NULL,
    
    -- 原始分配人（用于委托场景）
    original_assignee VARCHAR(64),
    
    -- 委托给的用户ID
    delegated_to VARCHAR(64),
    
    -- 委托人ID
    delegated_by VARCHAR(64),
    
    -- 委托时间
    delegated_time TIMESTAMP,
    
    -- 委托原因
    delegation_reason VARCHAR(500),
    
    -- 认领用户ID
    claimed_by VARCHAR(64),
    
    -- 认领时间
    claimed_time TIMESTAMP,
    
    -- 任务优先级（0-100，数值越大优先级越高）
    priority INTEGER DEFAULT 50,
    
    -- 任务到期时间
    due_date TIMESTAMP,
    
    -- 任务状态
    -- CREATED - 已创建
    -- ASSIGNED - 已分配
    -- CLAIMED - 已认领
    -- DELEGATED - 已委托
    -- IN_PROGRESS - 处理中
    -- COMPLETED - 已完成
    -- CANCELLED - 已取消
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    
    -- 任务创建时间
    created_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- 任务更新时间
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    -- 任务完成时间
    completed_time TIMESTAMP,
    
    -- 完成用户ID
    completed_by VARCHAR(64),
    
    -- 表单键
    form_key VARCHAR(255),
    
    -- 业务键
    business_key VARCHAR(255),
    
    -- 扩展属性（JSON格式）
    extended_properties TEXT,
    
    -- 租户ID（多租户支持）
    tenant_id VARCHAR(64),
    
    -- 版本号（乐观锁）
    version BIGINT DEFAULT 0,
    
    -- 是否已删除（软删除）
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- 创建人
    created_by VARCHAR(64),
    
    -- 更新人
    updated_by VARCHAR(64)
);

-- =====================================================
-- 创建索引以优化查询性能
-- =====================================================

-- 任务ID唯一索引（已在表定义中创建）
-- CREATE UNIQUE INDEX idx_task_id ON wf_extended_task_info(task_id);

-- 分配类型索引
CREATE INDEX idx_assignment_type ON wf_extended_task_info(assignment_type);

-- 分配目标索引
CREATE INDEX idx_assignment_target ON wf_extended_task_info(assignment_target);

-- 委托用户索引
CREATE INDEX idx_delegated_to ON wf_extended_task_info(delegated_to);

-- 认领用户索引
CREATE INDEX idx_claimed_by ON wf_extended_task_info(claimed_by);

-- 流程实例索引
CREATE INDEX idx_process_instance ON wf_extended_task_info(process_instance_id);

-- 创建时间索引
CREATE INDEX idx_created_time ON wf_extended_task_info(created_time);

-- 到期时间索引
CREATE INDEX idx_due_date ON wf_extended_task_info(due_date);

-- 优先级索引
CREATE INDEX idx_priority ON wf_extended_task_info(priority);

-- 状态索引
CREATE INDEX idx_status ON wf_extended_task_info(status);

-- 软删除索引
CREATE INDEX idx_is_deleted ON wf_extended_task_info(is_deleted);

-- 租户ID索引
CREATE INDEX idx_tenant_id ON wf_extended_task_info(tenant_id);

-- 复合索引：用户待办任务查询优化
CREATE INDEX idx_user_todo_tasks ON wf_extended_task_info(assignment_type, assignment_target, status, is_deleted);

-- 复合索引：委托任务查询优化
CREATE INDEX idx_delegated_tasks ON wf_extended_task_info(delegated_to, status, is_deleted);

-- 复合索引：认领任务查询优化
CREATE INDEX idx_claimed_tasks ON wf_extended_task_info(claimed_by, status, is_deleted);

-- 复合索引：虚拟组任务查询优化
CREATE INDEX idx_virtual_group_tasks ON wf_extended_task_info(assignment_type, assignment_target, claimed_by, status, is_deleted);

-- 复合索引：部门角色任务查询优化
CREATE INDEX idx_dept_role_tasks ON wf_extended_task_info(assignment_type, assignment_target, claimed_by, status, is_deleted);

-- 复合索引：过期任务查询优化
CREATE INDEX idx_overdue_tasks ON wf_extended_task_info(due_date, status, is_deleted);

-- 复合索引：优先级任务查询优化
CREATE INDEX idx_priority_tasks ON wf_extended_task_info(priority, status, is_deleted, created_time);

-- =====================================================
-- 创建触发器以自动更新updated_time字段
-- =====================================================

-- 创建更新时间戳函数
CREATE OR REPLACE FUNCTION update_updated_time_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- 创建触发器
CREATE TRIGGER update_wf_extended_task_info_updated_time
    BEFORE UPDATE ON wf_extended_task_info
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_time_column();

-- =====================================================
-- 添加表注释和字段注释
-- =====================================================

COMMENT ON TABLE wf_extended_task_info IS '扩展任务信息表 - 支持多维度任务分配的扩展数据模型';

COMMENT ON COLUMN wf_extended_task_info.id IS '主键ID';
COMMENT ON COLUMN wf_extended_task_info.task_id IS 'Flowable任务ID（关联到ACT_RU_TASK表）';
COMMENT ON COLUMN wf_extended_task_info.process_instance_id IS '流程实例ID';
COMMENT ON COLUMN wf_extended_task_info.process_definition_id IS '流程定义ID';
COMMENT ON COLUMN wf_extended_task_info.task_definition_key IS '任务定义键';
COMMENT ON COLUMN wf_extended_task_info.task_name IS '任务名称';
COMMENT ON COLUMN wf_extended_task_info.task_description IS '任务描述';
COMMENT ON COLUMN wf_extended_task_info.assignment_type IS '任务分配类型：USER(用户), VIRTUAL_GROUP(虚拟组), DEPT_ROLE(部门角色)';
COMMENT ON COLUMN wf_extended_task_info.assignment_target IS '分配目标：用户ID、虚拟组ID或部门ID:角色ID格式';
COMMENT ON COLUMN wf_extended_task_info.original_assignee IS '原始分配人（用于委托场景）';
COMMENT ON COLUMN wf_extended_task_info.delegated_to IS '委托给的用户ID';
COMMENT ON COLUMN wf_extended_task_info.delegated_by IS '委托人ID';
COMMENT ON COLUMN wf_extended_task_info.delegated_time IS '委托时间';
COMMENT ON COLUMN wf_extended_task_info.delegation_reason IS '委托原因';
COMMENT ON COLUMN wf_extended_task_info.claimed_by IS '认领用户ID';
COMMENT ON COLUMN wf_extended_task_info.claimed_time IS '认领时间';
COMMENT ON COLUMN wf_extended_task_info.priority IS '任务优先级（0-100，数值越大优先级越高）';
COMMENT ON COLUMN wf_extended_task_info.due_date IS '任务到期时间';
COMMENT ON COLUMN wf_extended_task_info.status IS '任务状态：CREATED/ASSIGNED/CLAIMED/DELEGATED/IN_PROGRESS/COMPLETED/CANCELLED';
COMMENT ON COLUMN wf_extended_task_info.created_time IS '任务创建时间';
COMMENT ON COLUMN wf_extended_task_info.updated_time IS '任务更新时间';
COMMENT ON COLUMN wf_extended_task_info.completed_time IS '任务完成时间';
COMMENT ON COLUMN wf_extended_task_info.completed_by IS '完成用户ID';
COMMENT ON COLUMN wf_extended_task_info.form_key IS '表单键';
COMMENT ON COLUMN wf_extended_task_info.business_key IS '业务键';
COMMENT ON COLUMN wf_extended_task_info.extended_properties IS '扩展属性（JSON格式）';
COMMENT ON COLUMN wf_extended_task_info.tenant_id IS '租户ID（多租户支持）';
COMMENT ON COLUMN wf_extended_task_info.version IS '版本号（乐观锁）';
COMMENT ON COLUMN wf_extended_task_info.is_deleted IS '是否已删除（软删除）';
COMMENT ON COLUMN wf_extended_task_info.created_by IS '创建人';
COMMENT ON COLUMN wf_extended_task_info.updated_by IS '更新人';

-- =====================================================
-- 创建约束以确保数据完整性
-- =====================================================

-- 检查分配类型的有效性
ALTER TABLE wf_extended_task_info 
ADD CONSTRAINT chk_assignment_type 
CHECK (assignment_type IN ('USER', 'VIRTUAL_GROUP', 'DEPT_ROLE'));

-- 检查任务状态的有效性
ALTER TABLE wf_extended_task_info 
ADD CONSTRAINT chk_status 
CHECK (status IN ('CREATED', 'ASSIGNED', 'CLAIMED', 'DELEGATED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED'));

-- 检查优先级范围
ALTER TABLE wf_extended_task_info 
ADD CONSTRAINT chk_priority 
CHECK (priority >= 0 AND priority <= 100);

-- 检查版本号非负
ALTER TABLE wf_extended_task_info 
ADD CONSTRAINT chk_version 
CHECK (version >= 0);

-- =====================================================
-- 插入初始化数据（如果需要）
-- =====================================================

-- 这里可以插入一些初始化的配置数据或示例数据
-- 目前暂时不需要初始化数据

-- =====================================================
-- 脚本执行完成
-- =====================================================

-- 输出执行结果
DO $$
BEGIN
    RAISE NOTICE '扩展任务信息表创建完成！';
    RAISE NOTICE '表名: wf_extended_task_info';
    RAISE NOTICE '索引数量: 15个';
    RAISE NOTICE '约束数量: 4个';
    RAISE NOTICE '触发器数量: 1个';
END $$;