-- =============================================================================
-- 17-Multi-Instance-Subtask-Demo: Create Physical Table for ACTION table "Meeting Remark"
--
-- Table Design (dw_table_definitions/dw_field_definitions) only creates metadata;
-- it never executes physical DDL (see deploy/init-scripts/16-meeting-participant-collection/
-- 06-create-physical-tables.sql for the established precedent). This script creates the
-- actual meeting_remark table so the FORM_POPUP "Add Remark" action can persist real rows.
--
-- Column set mirrors dw_field_definitions for table_id = the "Meeting Remark" ACTION table
-- under FU fu-20260422-23tfag: id (PK, uuid), main_id (FK to the request id string —
-- the process's __request_id / id variable, not a physical PRIMARY table row since this
-- FU's PRIMARY table has no physical backing table either), remark_type, remark_content,
-- building, room_name, plus the four platform-managed audit columns.
-- =============================================================================

CREATE TABLE IF NOT EXISTS meeting_remark (
    id VARCHAR(255) PRIMARY KEY,
    main_id VARCHAR(255),
    remark_type VARCHAR(255),
    remark_content TEXT,
    building VARCHAR(255),
    room_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(64),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_meeting_remark_main_id ON meeting_remark (main_id);
