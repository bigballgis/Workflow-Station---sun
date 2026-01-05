-- 功能单元表
CREATE TABLE admin_function_units (
    id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,
    version VARCHAR(20) NOT NULL,
    description TEXT,
    package_path VARCHAR(500),
    package_size BIGINT,
    checksum VARCHAR(64),
    digital_signature TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    imported_at TIMESTAMP,
    imported_by VARCHAR(64),
    validated_at TIMESTAMP,
    validated_by VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64),
    
    CONSTRAINT uk_function_unit_code_version UNIQUE(code, version)
);

CREATE INDEX idx_func_unit_code ON admin_function_units(code);
CREATE INDEX idx_func_unit_status ON admin_function_units(status);
CREATE INDEX idx_func_unit_imported_at ON admin_function_units(imported_at);

-- 功能单元部署记录表
CREATE TABLE admin_function_unit_deployments (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    environment VARCHAR(20) NOT NULL,
    strategy VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    deployed_at TIMESTAMP,
    deployed_by VARCHAR(64),
    completed_at TIMESTAMP,
    rollback_to_id VARCHAR(64),
    error_message TEXT,
    deployment_log TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    
    CONSTRAINT fk_deployment_function_unit FOREIGN KEY (function_unit_id) 
        REFERENCES admin_function_units(id)
);

CREATE INDEX idx_deployment_func_unit ON admin_function_unit_deployments(function_unit_id);
CREATE INDEX idx_deployment_env ON admin_function_unit_deployments(environment);
CREATE INDEX idx_deployment_status ON admin_function_unit_deployments(status);
CREATE INDEX idx_deployment_deployed_at ON admin_function_unit_deployments(deployed_at);

-- 功能单元审批记录表
CREATE TABLE admin_function_unit_approvals (
    id VARCHAR(64) PRIMARY KEY,
    deployment_id VARCHAR(64) NOT NULL,
    approval_type VARCHAR(20) NOT NULL,
    approver_id VARCHAR(64) NOT NULL,
    approver_name VARCHAR(100),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    comment TEXT,
    approved_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_approval_deployment FOREIGN KEY (deployment_id) 
        REFERENCES admin_function_unit_deployments(id)
);

CREATE INDEX idx_approval_deployment ON admin_function_unit_approvals(deployment_id);
CREATE INDEX idx_approval_approver ON admin_function_unit_approvals(approver_id);
CREATE INDEX idx_approval_status ON admin_function_unit_approvals(status);

-- 功能单元依赖关系表
CREATE TABLE admin_function_unit_dependencies (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    dependency_code VARCHAR(50) NOT NULL,
    dependency_version VARCHAR(20) NOT NULL,
    dependency_type VARCHAR(20) NOT NULL DEFAULT 'REQUIRED',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_dependency_function_unit FOREIGN KEY (function_unit_id) 
        REFERENCES admin_function_units(id)
);

CREATE INDEX idx_dependency_func_unit ON admin_function_unit_dependencies(function_unit_id);
CREATE INDEX idx_dependency_code ON admin_function_unit_dependencies(dependency_code);

-- 功能单元内容表（存储功能包中的各类内容）
CREATE TABLE admin_function_unit_contents (
    id VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    content_name VARCHAR(200) NOT NULL,
    content_path VARCHAR(500),
    content_data TEXT,
    checksum VARCHAR(64),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_content_function_unit FOREIGN KEY (function_unit_id) 
        REFERENCES admin_function_units(id)
);

CREATE INDEX idx_content_func_unit ON admin_function_unit_contents(function_unit_id);
CREATE INDEX idx_content_type ON admin_function_unit_contents(content_type);

-- 添加注释
COMMENT ON TABLE admin_function_units IS '功能单元表';
COMMENT ON COLUMN admin_function_units.status IS '状态: DRAFT-草稿, VALIDATED-已验证, DEPLOYED-已部署, DEPRECATED-已废弃';

COMMENT ON TABLE admin_function_unit_deployments IS '功能单元部署记录表';
COMMENT ON COLUMN admin_function_unit_deployments.environment IS '环境: DEV-开发, TEST-测试, STAGING-预生产, PRODUCTION-生产';
COMMENT ON COLUMN admin_function_unit_deployments.strategy IS '部署策略: FULL-全量, INCREMENTAL-增量, CANARY-灰度, BLUE_GREEN-蓝绿';
COMMENT ON COLUMN admin_function_unit_deployments.status IS '状态: PENDING-待部署, IN_PROGRESS-部署中, SUCCESS-成功, FAILED-失败, ROLLED_BACK-已回滚';

COMMENT ON TABLE admin_function_unit_approvals IS '功能单元审批记录表';
COMMENT ON COLUMN admin_function_unit_approvals.approval_type IS '审批类型: BUSINESS-业务审批, TECHNICAL-技术审批, SECURITY-安全审批';
COMMENT ON COLUMN admin_function_unit_approvals.status IS '状态: PENDING-待审批, APPROVED-已批准, REJECTED-已拒绝';

COMMENT ON TABLE admin_function_unit_dependencies IS '功能单元依赖关系表';
COMMENT ON COLUMN admin_function_unit_dependencies.dependency_type IS '依赖类型: REQUIRED-必需, OPTIONAL-可选';

COMMENT ON TABLE admin_function_unit_contents IS '功能单元内容表';
COMMENT ON COLUMN admin_function_unit_contents.content_type IS '内容类型: PROCESS-流程定义, FORM-表单, DATA_TABLE-数据表, SCRIPT-脚本';
