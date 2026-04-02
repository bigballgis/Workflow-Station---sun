-- =====================================================
-- Multi-Instance Execution Table
-- Tracks multi-instance sub-process execution metadata
-- for dynamic task dispatch based on sub-table data
-- =====================================================

-- =====================================================
-- wf_multi_instance_execution
-- Records multi-instance sub-process execution state
-- =====================================================
CREATE TABLE IF NOT EXISTS wf_multi_instance_execution (
    id BIGSERIAL PRIMARY KEY,
    process_instance_id VARCHAR(64) NOT NULL,
    activity_id VARCHAR(255) NOT NULL,
    activity_name VARCHAR(255),
    sub_table_name VARCHAR(100) NOT NULL,
    sub_table_id VARCHAR(64) NOT NULL,
    collection_variable_name VARCHAR(255) NOT NULL,
    execution_mode VARCHAR(20) NOT NULL DEFAULT 'PARALLEL',
    total_instances INTEGER NOT NULL,
    completed_instances INTEGER NOT NULL DEFAULT 0,
    active_instances INTEGER NOT NULL DEFAULT 0,
    cancelled_instances INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    started_time TIMESTAMP NOT NULL,
    completed_time TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mi_exec_mode CHECK (execution_mode IN ('PARALLEL', 'SEQUENTIAL')),
    CONSTRAINT chk_mi_exec_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED'))
);

-- Indexes for wf_multi_instance_execution
CREATE INDEX IF NOT EXISTS idx_mi_exec_process_instance ON wf_multi_instance_execution(process_instance_id);
CREATE INDEX IF NOT EXISTS idx_mi_exec_status ON wf_multi_instance_execution(status);
CREATE INDEX IF NOT EXISTS idx_mi_exec_activity_id ON wf_multi_instance_execution(activity_id);
CREATE INDEX IF NOT EXISTS idx_mi_exec_started_time ON wf_multi_instance_execution(started_time);

-- Comments
COMMENT ON TABLE wf_multi_instance_execution IS '多实例子流程执行记录表，用于跟踪基于子表数据的动态任务分发';
COMMENT ON COLUMN wf_multi_instance_execution.process_instance_id IS '流程实例ID';
COMMENT ON COLUMN wf_multi_instance_execution.activity_id IS 'BPMN活动节点ID';
COMMENT ON COLUMN wf_multi_instance_execution.activity_name IS 'BPMN活动节点名称';
COMMENT ON COLUMN wf_multi_instance_execution.sub_table_name IS '关联的子表物理表名';
COMMENT ON COLUMN wf_multi_instance_execution.sub_table_id IS '关联的子表ID（来自TableDefinition）';
COMMENT ON COLUMN wf_multi_instance_execution.collection_variable_name IS 'Flowable集合变量名称';
COMMENT ON COLUMN wf_multi_instance_execution.execution_mode IS '执行模式: PARALLEL（并行）, SEQUENTIAL（顺序）';
COMMENT ON COLUMN wf_multi_instance_execution.total_instances IS '总实例数（等于子表数据行数）';
COMMENT ON COLUMN wf_multi_instance_execution.completed_instances IS '已完成实例数';
COMMENT ON COLUMN wf_multi_instance_execution.active_instances IS '活跃实例数';
COMMENT ON COLUMN wf_multi_instance_execution.cancelled_instances IS '已取消实例数';
COMMENT ON COLUMN wf_multi_instance_execution.status IS '执行状态: ACTIVE（执行中）, COMPLETED（已完成）, CANCELLED（已取消）';
COMMENT ON COLUMN wf_multi_instance_execution.started_time IS '开始执行时间';
COMMENT ON COLUMN wf_multi_instance_execution.completed_time IS '完成时间';
