-- Store uploaded attachments in PostgreSQL instead of filesystem/PVC.
CREATE TABLE IF NOT EXISTS dw_uploaded_files (
    id BIGSERIAL PRIMARY KEY,
    stored_name VARCHAR(150) NOT NULL UNIQUE,
    original_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(255),
    file_size BIGINT NOT NULL,
    content BYTEA NOT NULL,
    created_by VARCHAR(64),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lock_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_dw_uploaded_files_created_at ON dw_uploaded_files(created_at DESC);

COMMENT ON TABLE dw_uploaded_files IS 'Database-backed uploaded files';
COMMENT ON COLUMN dw_uploaded_files.stored_name IS 'Opaque filename token exposed in /api/v1/upload/files/{storedName}';
