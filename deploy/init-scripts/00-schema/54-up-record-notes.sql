-- =====================================================
-- RecordNote: rich-text comments + file attachments
-- Dataverse annotation-style single table. One row is either a
-- COMMENT (rich text) or an ATTACHMENT (binary file); attachments
-- belonging to a comment reference it via parent_note_id.
-- Notes never cross process instances; stream key = (target_type, target_id, table_id):
--   target_type = 'TABLE'  -> target_id = process instance id (the hosting
--                             table's shared stream within that one process)
--   target_type = 'RECORD' -> target_id = sub-table row id
-- =====================================================

CREATE TABLE IF NOT EXISTS up_record_note (
    id                VARCHAR(64) PRIMARY KEY,
    target_type       VARCHAR(20)  NOT NULL,
    target_id         VARCHAR(64)  NOT NULL,
    table_kind        VARCHAR(10)  NOT NULL DEFAULT 'DW',
    table_id          VARCHAR(64)  NOT NULL,
    function_unit_id  VARCHAR(64),

    note_type         VARCHAR(20)  NOT NULL DEFAULT 'COMMENT',
    parent_note_id    VARCHAR(64)  REFERENCES up_record_note(id) ON DELETE CASCADE,

    subject           VARCHAR(255),
    body_html         TEXT,
    body_text         TEXT,

    file_name         VARCHAR(255),
    mime_type         VARCHAR(255),
    file_size         BIGINT,
    file_content      BYTEA,
    is_inline_image   BOOLEAN      NOT NULL DEFAULT FALSE,

    created_by        VARCHAR(64)  NOT NULL,
    created_by_name   VARCHAR(100),
    created_at        TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        VARCHAR(64),
    updated_at        TIMESTAMP(6),
    is_deleted        BOOLEAN      NOT NULL DEFAULT FALSE,
    lock_version      BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT chk_record_note_target_type CHECK (target_type IN ('TABLE', 'RECORD')),
    CONSTRAINT chk_record_note_table_kind CHECK (table_kind IN ('DW', 'RT')),
    CONSTRAINT chk_record_note_shape CHECK (
        (note_type = 'COMMENT'    AND body_html IS NOT NULL)
     OR (note_type = 'ATTACHMENT' AND file_name IS NOT NULL AND file_content IS NOT NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_up_record_note_target
    ON up_record_note (target_type, target_id, created_at DESC)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_up_record_note_parent
    ON up_record_note (parent_note_id);

CREATE INDEX IF NOT EXISTS idx_up_record_note_fu
    ON up_record_note (function_unit_id);

COMMENT ON TABLE up_record_note IS 'Record-level notes: rich-text comments and file attachments (Dataverse annotation-style single table)';
COMMENT ON COLUMN up_record_note.target_type IS 'Stream key = (target_type, target_id, table_id), never crossing process instances. TABLE = per-instance table stream (target_id = process instance id); RECORD = single row notes (target_id = row PK)';
COMMENT ON COLUMN up_record_note.table_kind IS 'ID space of table_id: DW = dw_table_definitions, RT = rt_table_definitions';
COMMENT ON COLUMN up_record_note.note_type IS 'COMMENT = rich-text note; ATTACHMENT = binary file (parent_note_id links it to its comment, NULL for standalone uploads)';
COMMENT ON COLUMN up_record_note.is_inline_image IS 'TRUE when the attachment is an inline image referenced from a comment body, hidden from the attachment list';
COMMENT ON COLUMN up_record_note.body_text IS 'Plain-text extraction of body_html for list summaries and search';
