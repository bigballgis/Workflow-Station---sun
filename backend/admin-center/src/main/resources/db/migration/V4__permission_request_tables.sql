-- V4: Permission Request and Approval System Tables
-- This migration creates tables for virtual group roles, business unit roles, approvers, and permission requests

-- 1. Virtual Group Roles - 虚拟组角色绑定
CREATE TABLE IF NOT EXISTS sys_virtual_group_roles (
    id VARCHAR(64) PRIMARY KEY,
    virtual_group_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    UNIQUE (virtual_group_id, role_id),
    CONSTRAINT fk_vgr_virtual_group FOREIGN KEY (virtual_group_id) REFERENCES sys_virtual_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_vgr_role FOREIGN KEY (role_id) REFERENCES sys_roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_vgr_virtual_group_id ON sys_virtual_group_roles(virtual_group_id);
CREATE INDEX IF NOT EXISTS idx_vgr_role_id ON sys_virtual_group_roles(role_id);

-- 2. Business Unit Roles - 业务单元角色绑定
CREATE TABLE IF NOT EXISTS sys_business_unit_roles (
    id VARCHAR(64) PRIMARY KEY,
    business_unit_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    UNIQUE (business_unit_id, role_id),
    CONSTRAINT fk_bur_business_unit FOREIGN KEY (business_unit_id) REFERENCES sys_business_units(id) ON DELETE CASCADE,
    CONSTRAINT fk_bur_role FOREIGN KEY (role_id) REFERENCES sys_roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_bur_business_unit_id ON sys_business_unit_roles(business_unit_id);
CREATE INDEX IF NOT EXISTS idx_bur_role_id ON sys_business_unit_roles(role_id);

-- 3. User Business Unit Roles - 用户业务单元角色分配
CREATE TABLE IF NOT EXISTS sys_user_business_unit_roles (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    business_unit_id VARCHAR(64) NOT NULL,
    role_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    UNIQUE (user_id, business_unit_id, role_id),
    CONSTRAINT fk_ubur_user FOREIGN KEY (user_id) REFERENCES sys_users(id) ON DELETE CASCADE,
    CONSTRAINT fk_ubur_business_unit FOREIGN KEY (business_unit_id) REFERENCES sys_business_units(id) ON DELETE CASCADE,
    CONSTRAINT fk_ubur_role FOREIGN KEY (role_id) REFERENCES sys_roles(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_ubur_user_id ON sys_user_business_unit_roles(user_id);
CREATE INDEX IF NOT EXISTS idx_ubur_business_unit_id ON sys_user_business_unit_roles(business_unit_id);
CREATE INDEX IF NOT EXISTS idx_ubur_role_id ON sys_user_business_unit_roles(role_id);

-- 4. Approvers - 审批人配置
CREATE TABLE IF NOT EXISTS sys_approvers (
    id VARCHAR(64) PRIMARY KEY,
    target_type VARCHAR(20) NOT NULL,  -- VIRTUAL_GROUP, BUSINESS_UNIT
    target_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    UNIQUE (target_type, target_id, user_id),
    CONSTRAINT fk_approver_user FOREIGN KEY (user_id) REFERENCES sys_users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_approver_target ON sys_approvers(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_approver_user_id ON sys_approvers(user_id);

-- 5. Permission Requests - 权限申请
CREATE TABLE IF NOT EXISTS sys_permission_requests (
    id VARCHAR(64) PRIMARY KEY,
    applicant_id VARCHAR(64) NOT NULL,
    request_type VARCHAR(20) NOT NULL,  -- VIRTUAL_GROUP, BUSINESS_UNIT_ROLE
    target_id VARCHAR(64) NOT NULL,
    role_ids TEXT,  -- JSON array of role IDs (for BUSINESS_UNIT_ROLE type)
    reason TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING, APPROVED, REJECTED, CANCELLED
    approver_id VARCHAR(64),
    approver_comment TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    approved_at TIMESTAMP,
    CONSTRAINT fk_pr_applicant FOREIGN KEY (applicant_id) REFERENCES sys_users(id)
);

CREATE INDEX IF NOT EXISTS idx_pr_applicant_id ON sys_permission_requests(applicant_id);
CREATE INDEX IF NOT EXISTS idx_pr_status ON sys_permission_requests(status);
CREATE INDEX IF NOT EXISTS idx_pr_request_type ON sys_permission_requests(request_type);
CREATE INDEX IF NOT EXISTS idx_pr_target_id ON sys_permission_requests(target_id);

-- 6. Member Change Logs - 成员变更记录（审计日志）
CREATE TABLE IF NOT EXISTS sys_member_change_logs (
    id VARCHAR(64) PRIMARY KEY,
    change_type VARCHAR(20) NOT NULL,  -- JOIN, EXIT, REMOVED
    target_type VARCHAR(20) NOT NULL,  -- VIRTUAL_GROUP, BUSINESS_UNIT
    target_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    role_ids TEXT,  -- JSON array of role IDs
    operator_id VARCHAR(64),  -- 操作人（审批人或用户自己）
    reason TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mcl_target ON sys_member_change_logs(target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_mcl_user_id ON sys_member_change_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_mcl_change_type ON sys_member_change_logs(change_type);
CREATE INDEX IF NOT EXISTS idx_mcl_created_at ON sys_member_change_logs(created_at);
