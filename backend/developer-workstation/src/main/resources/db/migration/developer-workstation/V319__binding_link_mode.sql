-- V319: bindingLinkMode separates structural FK from MI participant row (PRD §10, S6)

ALTER TABLE dw_form_table_bindings
    ADD COLUMN IF NOT EXISTS binding_link_mode VARCHAR(32) NOT NULL DEFAULT 'structuralFk';
