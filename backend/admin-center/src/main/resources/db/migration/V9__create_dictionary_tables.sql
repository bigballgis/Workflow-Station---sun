-- 数据字典表
CREATE TABLE IF NOT EXISTS admin_dictionaries (
    id VARCHAR(36) PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    data_source_type VARCHAR(20),
    data_source_config TEXT,
    cache_ttl INTEGER DEFAULT 0,
    version INTEGER NOT NULL DEFAULT 1,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36)
);

-- 数据字典项表
CREATE TABLE IF NOT EXISTS admin_dictionary_items (
    id VARCHAR(36) PRIMARY KEY,
    dictionary_id VARCHAR(36) NOT NULL,
    parent_id VARCHAR(36),
    item_code VARCHAR(50) NOT NULL,
    name VARCHAR(200) NOT NULL,
    name_en VARCHAR(200),
    name_zh_cn VARCHAR(200),
    name_zh_tw VARCHAR(200),
    value VARCHAR(500),
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    valid_from TIMESTAMP,
    valid_to TIMESTAMP,
    ext_attributes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(36),
    CONSTRAINT fk_dict_item_dict FOREIGN KEY (dictionary_id) REFERENCES admin_dictionaries(id),
    CONSTRAINT fk_dict_item_parent FOREIGN KEY (parent_id) REFERENCES admin_dictionary_items(id)
);

-- 数据字典版本历史表
CREATE TABLE IF NOT EXISTS admin_dictionary_versions (
    id VARCHAR(36) PRIMARY KEY,
    dictionary_id VARCHAR(36) NOT NULL,
    version INTEGER NOT NULL,
    snapshot_data TEXT NOT NULL,
    change_description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(36),
    CONSTRAINT uk_dict_version UNIQUE (dictionary_id, version)
);

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_dict_code ON admin_dictionaries(code);
CREATE INDEX IF NOT EXISTS idx_dict_type ON admin_dictionaries(type);
CREATE INDEX IF NOT EXISTS idx_dict_status ON admin_dictionaries(status);

CREATE INDEX IF NOT EXISTS idx_dict_item_dict_id ON admin_dictionary_items(dictionary_id);
CREATE INDEX IF NOT EXISTS idx_dict_item_code ON admin_dictionary_items(dictionary_id, item_code);
CREATE INDEX IF NOT EXISTS idx_dict_item_parent ON admin_dictionary_items(parent_id);
CREATE INDEX IF NOT EXISTS idx_dict_item_status ON admin_dictionary_items(status);

CREATE INDEX IF NOT EXISTS idx_dict_ver_dict_id ON admin_dictionary_versions(dictionary_id);
CREATE INDEX IF NOT EXISTS idx_dict_ver_version ON admin_dictionary_versions(dictionary_id, version);

-- 插入系统字典示例数据
INSERT INTO admin_dictionaries (id, code, name, description, type, status, version, sort_order, created_at, updated_at)
VALUES 
    ('sys-dict-001', 'USER_STATUS', '用户状态', '用户账户状态字典', 'SYSTEM', 'ACTIVE', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-dict-002', 'GENDER', '性别', '性别字典', 'SYSTEM', 'ACTIVE', 1, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-dict-003', 'LANGUAGE', '语言', '系统支持的语言', 'SYSTEM', 'ACTIVE', 1, 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;

-- 插入系统字典项示例数据
INSERT INTO admin_dictionary_items (id, dictionary_id, item_code, name, name_en, name_zh_cn, name_zh_tw, value, status, sort_order, created_at, updated_at)
VALUES 
    -- 用户状态
    ('sys-item-001', 'sys-dict-001', 'ACTIVE', '启用', 'Active', '启用', '啟用', 'ACTIVE', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-002', 'sys-dict-001', 'INACTIVE', '禁用', 'Inactive', '禁用', '禁用', 'INACTIVE', 'ACTIVE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-003', 'sys-dict-001', 'LOCKED', '锁定', 'Locked', '锁定', '鎖定', 'LOCKED', 'ACTIVE', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- 性别
    ('sys-item-004', 'sys-dict-002', 'MALE', '男', 'Male', '男', '男', 'M', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-005', 'sys-dict-002', 'FEMALE', '女', 'Female', '女', '女', 'F', 'ACTIVE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-006', 'sys-dict-002', 'OTHER', '其他', 'Other', '其他', '其他', 'O', 'ACTIVE', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    -- 语言
    ('sys-item-007', 'sys-dict-003', 'EN', '英文', 'English', '英文', '英文', 'en', 'ACTIVE', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-008', 'sys-dict-003', 'ZH_CN', '简体中文', 'Simplified Chinese', '简体中文', '簡體中文', 'zh-CN', 'ACTIVE', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('sys-item-009', 'sys-dict-003', 'ZH_TW', '繁体中文', 'Traditional Chinese', '繁体中文', '繁體中文', 'zh-TW', 'ACTIVE', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
