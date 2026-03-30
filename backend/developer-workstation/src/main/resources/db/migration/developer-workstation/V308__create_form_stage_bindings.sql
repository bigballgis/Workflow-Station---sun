CREATE TABLE dw_form_stage_bindings (
  id            BIGSERIAL PRIMARY KEY,
  form_id       BIGINT NOT NULL REFERENCES dw_form_definitions(id) ON DELETE CASCADE,
  stage_id      VARCHAR(255) NOT NULL,
  stage_name    VARCHAR(255),
  created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(form_id, stage_id)
);

CREATE INDEX idx_form_stage_bindings_stage_id ON dw_form_stage_bindings(stage_id);
