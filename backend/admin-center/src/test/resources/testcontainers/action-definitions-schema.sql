-- 容器化持久层测试用的最小 schema。
--
-- 刻意**照抄** deploy/init-scripts/00-schema/ 的真实 DDL，而不是让 Hibernate
-- 按实体反推建表（ddl-auto=create）—— 否则测的只是"实体与它自己一致"，
-- 恰好绕开了这类测试真正要发现的问题：实体映射与线上 schema 漂移。
--
-- 来源：
--   sys_function_units        -> 00-schema/01-platform-security-schema.sql:517
--   sys_action_definitions    -> 00-schema/07-add-action-definitions-table.sql:11
--   description 列            -> 00-schema/37-sys-action-definitions-description.sql
--                                （该脚本把 display_name 改名为 description，此处直接采用改名后的形态）
--
-- 只建被测实体真正需要的两张表；sys_function_units 仅保留 NOT NULL 列 + 被引用的主键，
-- 其余列省略不影响 FK / 级联行为的验证。

CREATE TABLE IF NOT EXISTS sys_function_units (
    id          VARCHAR(64) PRIMARY KEY,
    code        VARCHAR(50)  NOT NULL,
    name        VARCHAR(100) NOT NULL,
    enabled     BOOLEAN      NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    description TEXT,
    created_at  TIMESTAMP(6) WITH TIME ZONE,
    created_by  VARCHAR(64),
    updated_at  TIMESTAMP(6) WITH TIME ZONE,
    updated_by  VARCHAR(64)
);

CREATE TABLE IF NOT EXISTS sys_action_definitions (
    id               VARCHAR(64) PRIMARY KEY,
    function_unit_id VARCHAR(64)  NOT NULL,
    action_name      VARCHAR(100) NOT NULL,
    action_type      VARCHAR(50)  NOT NULL,
    description      TEXT,
    config_json      JSONB DEFAULT '{}'::jsonb,
    icon             VARCHAR(50),
    button_color     VARCHAR(20),
    is_default       BOOLEAN DEFAULT false,
    created_at       TIMESTAMP(6) WITH TIME ZONE,
    updated_at       TIMESTAMP(6) WITH TIME ZONE,
    created_by       VARCHAR(64),
    updated_by       VARCHAR(64),
    CONSTRAINT fk_action_function_unit FOREIGN KEY (function_unit_id)
        REFERENCES sys_function_units(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_sys_action_function_unit ON sys_action_definitions(function_unit_id);
CREATE INDEX IF NOT EXISTS idx_sys_action_name ON sys_action_definitions(action_name);
CREATE INDEX IF NOT EXISTS idx_sys_action_type ON sys_action_definitions(action_type);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_action_name_fu
    ON sys_action_definitions(function_unit_id, action_name);
