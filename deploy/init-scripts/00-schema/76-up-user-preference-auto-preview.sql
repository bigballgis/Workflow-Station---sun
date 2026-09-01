-- Per-user preference: auto-open the first previewable form file when opening a workflow form.
ALTER TABLE up_user_preference
    ADD COLUMN IF NOT EXISTS auto_preview_on_open BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN up_user_preference.auto_preview_on_open IS
    'When true, opening a task or request form opens the first previewable attachment. Default false.';
