-- =============================================================================
-- 16-meeting-participant-collection: Create Physical Tables
-- 会议参与人信息收集：创建物理数据表（meeting + participants）
--
-- The 01-create-tables.sql only creates metadata in dw_table_definitions.
-- This script creates the actual database tables needed at runtime.
--
-- Dependencies: 00-create-function-unit.sql, 01-create-tables.sql
-- Execution order: after 05 (or any time before running the flow)
-- =============================================================================

CREATE TABLE IF NOT EXISTS meeting (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(200) NOT NULL,
    meeting_time TIMESTAMP NOT NULL,
    location VARCHAR(200) NOT NULL DEFAULT '',
    organizer_name VARCHAR(100) NOT NULL DEFAULT '',
    description TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS participants (
    id BIGSERIAL PRIMARY KEY,
    meeting_id BIGINT NOT NULL REFERENCES meeting (id) ON DELETE CASCADE,
    name VARCHAR(500) NOT NULL DEFAULT '',
    department VARCHAR(500),
    email VARCHAR(500) NOT NULL DEFAULT '',
    assignee_user_id VARCHAR(64),
    attend_status VARCHAR(20) DEFAULT 'PENDING',
    dietary_preference VARCHAR(30),
    remark TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0,
    row_version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_participants_meeting_id ON participants (meeting_id);
