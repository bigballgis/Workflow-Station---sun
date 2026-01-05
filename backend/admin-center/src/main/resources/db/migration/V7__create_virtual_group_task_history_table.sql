-- 虚拟组任务处理历史表
CREATE TABLE admin_virtual_group_task_history (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    group_id VARCHAR(64),
    action_type VARCHAR(20) NOT NULL,
    from_user_id VARCHAR(64),
    to_user_id VARCHAR(64),
    reason TEXT,
    comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_vg_task_history_task ON admin_virtual_group_task_history(task_id);
CREATE INDEX idx_vg_task_history_group ON admin_virtual_group_task_history(group_id);
CREATE INDEX idx_vg_task_history_action ON admin_virtual_group_task_history(action_type);
CREATE INDEX idx_vg_task_history_from_user ON admin_virtual_group_task_history(from_user_id);
CREATE INDEX idx_vg_task_history_to_user ON admin_virtual_group_task_history(to_user_id);
CREATE INDEX idx_vg_task_history_time ON admin_virtual_group_task_history(created_at);
