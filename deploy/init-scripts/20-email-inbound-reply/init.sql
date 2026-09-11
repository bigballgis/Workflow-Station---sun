-- =============================================================================
-- 20-email-inbound-reply
-- EXECUTE_ENV: UAT ONLY
-- Do not run on DEV / SIT / PROD. Not loaded by 00-init-all.sh or init-database.ps1.
-- UAT operators apply this file manually with psql after schema/admin seed exist.
-- Function Unit: Email Inbound Reply (fu-20260910-emlrep)
-- Designer snapshot aligned to current_version 1.0.5 (seed status remains DRAFT).
-- Start → Review Inbound → Confirm & Send
--   ├ Send Email (APPROVE) → Send Task → End
--   └ Submit for Checker (REJECT) → Checker Review (Department Manager)
--        ├ Send Email (APPROVE) → Send Task → End
--        └ Back to Confirm & Send (REJECT) → Confirm & Send
-- Review / Confirm / Checker assignees: BU E2E_FINANCE + role MANAGER
--   Prerequisite: 01-admin/05-e2e-test-users-and-business-units.sql on the target DB.
-- Idempotent rebuild: DELETE existing dw_* for this FU (by code/name) then INSERT.
-- Destructive to an existing same-code design, including dw_versions.
-- Does not seed sys_function_units, credentials, or dw_versions snapshots.
-- =============================================================================

BEGIN;

CREATE TEMP TABLE IF NOT EXISTS tmp_eir_mail_keep (
    name TEXT PRIMARY KEY,
    credential_encrypted TEXT,
    username TEXT,
    host TEXT,
    port INT,
    from_email TEXT,
    from_name TEXT,
    use_tls BOOLEAN,
    enabled BOOLEAN,
    imap_host TEXT,
    imap_port INT,
    imap_use_ssl BOOLEAN,
    mailbox_address TEXT
);
DELETE FROM tmp_eir_mail_keep;

DO $cleanup$
DECLARE
    v_fu_id BIGINT;
BEGIN
    SELECT id INTO v_fu_id
    FROM dw_function_units
    WHERE code = 'fu-20260910-emlrep' OR name = 'Email Inbound Reply'
    ORDER BY id
    LIMIT 1;

    IF v_fu_id IS NULL THEN
        RETURN;
    END IF;

    INSERT INTO tmp_eir_mail_keep (
        name, credential_encrypted, username, host, port, from_email, from_name,
        use_tls, enabled, imap_host, imap_port, imap_use_ssl, mailbox_address
    )
    SELECT DISTINCT ON (name)
        name, credential_encrypted, username, host, port, from_email, from_name,
        use_tls, enabled, imap_host, imap_port, imap_use_ssl, mailbox_address
    FROM dw_email_connections
    WHERE function_unit_id = v_fu_id
    ORDER BY name, enabled DESC NULLS LAST, credential_encrypted DESC NULLS LAST;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'dw_main_table_view_access') THEN
        DELETE FROM dw_main_table_view_access
        WHERE view_config_id IN (
            SELECT id FROM dw_main_table_view_configs WHERE function_unit_id = v_fu_id
        );
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'dw_main_table_view_fields') THEN
        DELETE FROM dw_main_table_view_fields
        WHERE view_config_id IN (
            SELECT id FROM dw_main_table_view_configs WHERE function_unit_id = v_fu_id
        );
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'dw_main_table_view_configs') THEN
        DELETE FROM dw_main_table_view_configs WHERE function_unit_id = v_fu_id;
    END IF;

    DELETE FROM dw_form_stage_bindings
    WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id);

    DELETE FROM dw_sub_table_view_fields
    WHERE view_config_id IN (
        SELECT c.id FROM dw_sub_table_view_configs c
        WHERE c.binding_id IN (
            SELECT id FROM dw_form_table_bindings
            WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id)
        )
    );
    DELETE FROM dw_sub_table_view_configs
    WHERE binding_id IN (
        SELECT id FROM dw_form_table_bindings
        WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id)
    );

    DELETE FROM dw_form_table_bindings
    WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id);
    DELETE FROM dw_form_definitions WHERE function_unit_id = v_fu_id;
    DELETE FROM dw_process_definitions WHERE function_unit_id = v_fu_id;
    DELETE FROM dw_versions WHERE function_unit_id = v_fu_id;
    DELETE FROM dw_action_definitions WHERE function_unit_id = v_fu_id;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'dw_table_relations') THEN
        DELETE FROM dw_table_relations WHERE function_unit_id = v_fu_id;
    END IF;

    DELETE FROM dw_foreign_keys
    WHERE table_id IN (SELECT id FROM dw_table_definitions WHERE function_unit_id = v_fu_id)
       OR ref_table_id IN (SELECT id FROM dw_table_definitions WHERE function_unit_id = v_fu_id);
    DELETE FROM dw_pk_sequences
    WHERE table_id IN (SELECT id FROM dw_table_definitions WHERE function_unit_id = v_fu_id);
    DELETE FROM dw_field_definitions
    WHERE table_id IN (SELECT id FROM dw_table_definitions WHERE function_unit_id = v_fu_id);
    DELETE FROM dw_table_definitions WHERE function_unit_id = v_fu_id;

    DELETE FROM dw_email_monitor_rules WHERE function_unit_id = v_fu_id;
    DELETE FROM dw_email_templates WHERE function_unit_id = v_fu_id;
    DELETE FROM dw_email_connections WHERE function_unit_id = v_fu_id;
    DELETE FROM dw_function_units WHERE id = v_fu_id;
END
$cleanup$;

DO $main$
DECLARE
    v_fu_id              BIGINT;
    v_main_table_id      BIGINT;
    v_action_table_id    BIGINT;
    v_main_pk_field_id   BIGINT;
    v_action_fk_field_id BIGINT;
    v_form_inbound_id    BIGINT;
    v_form_review_id     BIGINT;
    v_form_confirm_id    BIGINT;
    v_form_popup_id      BIGINT;
    v_bind_popup_id      BIGINT;
    v_act_save_id        BIGINT;
    v_act_continue_id    BIGINT;
    v_act_popup_id       BIGINT;
    v_act_send_id        BIGINT;
    v_act_checker_id     BIGINT;
    v_act_reconfirm_id   BIGINT;
    v_tpl_id             BIGINT;
    v_monitor_tpl_id     BIGINT;
    v_view_id            BIGINT;
    v_in_uid             TEXT := '11111111-2222-4333-a444-555555555501';
    v_out_uid            TEXT := '11111111-2222-4333-a444-555555555502';
    v_rule_uid           TEXT := '11111111-2222-4333-a444-555555555503';
    v_tpl_rule_uid       TEXT := '11111111-2222-4333-a444-555555555504';
    v_extraction         JSONB;
    v_confirm_cfg        JSONB;
    v_popup_canvas       JSONB;
    v_bpmn_xml           TEXT;
    v_bpmn_b64           TEXT;
    v_empty_opts         TEXT :=
        '{"form":{"size":"default","inline":false,"labelWidth":"140px","showMessage":true,"labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":false,"innerText":"Submit"}}';
BEGIN
    -- -----------------------------------------------------------------
    -- Function Unit
    -- -----------------------------------------------------------------
    INSERT INTO dw_function_units (
        code, name, display_name, status,
        current_version, version, is_active, enabled,
        deployed_at, lock_version, created_by, created_at, updated_by, updated_at
    ) VALUES (
        'fu-20260910-emlrep',
        'Email Inbound Reply',
        'Inbound email is stored on the main table; Confirm can send now or send to Department Manager for checker review',
        'DRAFT',
        '1.0.5', '1.0.0',
        true, true,
        NULL, 0,
        'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_fu_id;

    -- -----------------------------------------------------------------
    -- Tables
    -- -----------------------------------------------------------------
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, display_name,
        request_id_config, created_at, updated_at
    ) VALUES (
        v_fu_id, 'email_inbound_case', 'Inbound Email Case', 'MAIN', 'Inbound email case',
        '{"fieldNames":["id"],"separator":"_"}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_main_table_id;

    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, display_name,
        created_at, updated_at
    ) VALUES (
        v_fu_id, 'email_send_confirm_log', 'Send Confirm Log', 'ACTION', 'FORM_POPUP confirmation log',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_action_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, nullable, is_primary_key, is_unique,
        display_name, sort_order, is_foreign_key, ref_table_id, ref_primary_key_fields,
        pk_generation_json, fk_display_mode
    ) VALUES
    (v_main_table_id, 'id',                  'VARCHAR',   64,  false, true,  true,  'Case ID',          0, false, NULL, NULL, '{"strategy":"prefixedSequence","prefix":"Email-","padWidth":6,"startValue":1,"scope":"perTable"}'::jsonb, 'readonly'),
    (v_main_table_id, 'sender_email',        'VARCHAR',   255, true,  false, false, 'From',             1, false, NULL, NULL, NULL, 'readonly'),
    (v_main_table_id, 'email_to',            'VARCHAR',   500, true,  false, false, 'To',               2, false, NULL, NULL, NULL, 'readonly'),
    (v_main_table_id, 'email_subject',       'VARCHAR',   500, true,  false, false, 'Subject',          3, false, NULL, NULL, NULL, 'readonly'),
    (v_main_table_id, 'email_body',          'TEXT',      NULL, true,  false, false, 'Body',             4, false, NULL, NULL, NULL, 'readonly'),
    (v_main_table_id, 'message_id',          'VARCHAR',   255, true,  false, false, 'Message-ID',       5, false, NULL, NULL, NULL, 'readonly'),
    (v_main_table_id, 'received_at',         'TIMESTAMP', NULL, true,  false, false, 'Received At',      6, false, NULL, NULL, NULL, 'readonly'),
    (v_main_table_id, 'inbound_attachment',  'FILE',      255, true,  false, false, 'Attachments',      7, false, NULL, NULL, NULL, 'readonly'),
    (v_main_table_id, 'need_reply',          'BOOLEAN',   NULL, true,  false, false, 'Need Reply',       8, false, NULL, NULL, NULL, 'readonly'),
    (v_main_table_id, 'reply_subject',       'VARCHAR',   500, true,  false, false, 'Reply Subject',    9, false, NULL, NULL, NULL, 'readonly'),
    (v_main_table_id, 'reply_body',          'TEXT',      NULL, true,  false, false, 'Reply Body',      10, false, NULL, NULL, NULL, 'readonly'),
    (v_main_table_id, 'created_by',          'VARCHAR',   64,  true,  false, false, 'Created By',      11, false, NULL, NULL, NULL, 'readonly'),
    (v_main_table_id, 'created_at',          'TIMESTAMP', NULL, true,  false, false, 'Created At',      12, false, NULL, NULL, NULL, 'readonly'),
    (v_main_table_id, 'updated_by',          'VARCHAR',   64,  true,  false, false, 'Updated By',      13, false, NULL, NULL, NULL, 'readonly'),
    (v_main_table_id, 'updated_at',          'TIMESTAMP', NULL, true,  false, false, 'Updated At',      14, false, NULL, NULL, NULL, 'readonly');

    SELECT id INTO v_main_pk_field_id
    FROM dw_field_definitions
    WHERE table_id = v_main_table_id AND field_name = 'id';

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, nullable, is_primary_key, is_unique,
        display_name, sort_order, is_foreign_key, ref_table_id, ref_primary_key_fields,
        pk_generation_json, fk_display_mode, relation_cardinality
    ) VALUES
    (v_action_table_id, 'id',            'VARCHAR',   64,  false, true,  true,  'Id',            0, false, NULL, NULL, '{"strategy":"uuid"}'::jsonb, 'readonly', NULL),
    (v_action_table_id, 'main_id',       'VARCHAR',   64,  true,  false, false, 'Request ID',    1, true,  v_main_table_id, '["id"]'::jsonb, NULL, 'readonly', 'oneToMany'),
    (v_action_table_id, 'confirm_note',  'TEXT',      NULL, true,  false, false, 'Confirm Note',  2, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_action_table_id, 'created_by',    'VARCHAR',   64,  true,  false, false, 'Created By',    3, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_action_table_id, 'created_at',    'TIMESTAMP', NULL, true,  false, false, 'Created At',    4, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_action_table_id, 'updated_by',    'VARCHAR',   64,  true,  false, false, 'Updated By',    5, false, NULL, NULL, NULL, 'readonly', NULL),
    (v_action_table_id, 'updated_at',    'TIMESTAMP', NULL, true,  false, false, 'Updated At',    6, false, NULL, NULL, NULL, 'readonly', NULL);

    SELECT id INTO v_action_fk_field_id
    FROM dw_field_definitions
    WHERE table_id = v_action_table_id AND field_name = 'main_id';

    INSERT INTO dw_foreign_keys (table_id, field_id, ref_table_id, ref_field_id, on_delete, on_update)
    VALUES (v_action_table_id, v_action_fk_field_id, v_main_table_id, v_main_pk_field_id, 'NO ACTION', 'NO ACTION');

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'dw_table_relations') THEN
        INSERT INTO dw_table_relations (
            function_unit_id, source_table_id, source_field_name, relation_type,
            target_table_id, target_field_name, created_at, updated_at
        ) VALUES (
            v_fu_id, v_action_table_id, 'main_id', 'ONE_TO_MANY',
            v_main_table_id, 'id', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
        );
    END IF;

    INSERT INTO dw_pk_sequences (
        table_id, field_name, scope_type, scope_key, prefix, pad_width, current_value
    ) VALUES (
        v_main_table_id, 'id', 'perTable', '', 'Email-', 6, 0
    );

    INSERT INTO dw_main_table_view_configs (
        function_unit_id, main_table_id, view_name, is_default,
        sort_config, filter_config, status, restrict_to_involved_users,
        created_at, updated_at
    ) VALUES (
        v_fu_id, v_main_table_id, 'Inbound Email Case', true,
        '[{"direction":"DESC","fieldName":"start_time","systemField":true}]'::jsonb,
        '{"conditions":[]}'::jsonb,
        'DRAFT', false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_view_id;

    INSERT INTO dw_main_table_view_fields (
        view_config_id, field_name, display_label, column_width, sort_order,
        visible, is_system_field, column_type
    ) VALUES
    (v_view_id, 'id',                 'Case ID',       150,  0, true, false, 'field'),
    (v_view_id, 'sender_email',       'From',          150,  1, true, false, 'field'),
    (v_view_id, 'email_to',           'To',             150,  2, true, false, 'field'),
    (v_view_id, 'email_subject',      'Subject',       150,  3, true, false, 'field'),
    (v_view_id, 'email_body',         'Body',          150,  4, true, false, 'field'),
    (v_view_id, 'message_id',         'Message-ID',     150,  5, true, false, 'field'),
    (v_view_id, 'received_at',        'Received At',    150,  6, true, false, 'field'),
    (v_view_id, 'inbound_attachment', 'Attachments',    150,  7, true, false, 'field'),
    (v_view_id, 'need_reply',         'Need Reply',     150,  8, true, false, 'field'),
    (v_view_id, 'reply_subject',      'Reply Subject',  150,  9, true, false, 'field'),
    (v_view_id, 'reply_body',         'Reply Body',     150, 10, true, false, 'field'),
    (v_view_id, 'created_by',        'Created By',     150, 11, true, false, 'field'),
    (v_view_id, 'created_at',        'Created At',     150, 12, true, false, 'field'),
    (v_view_id, 'updated_by',        'Updated By',     150, 13, true, false, 'field'),
    (v_view_id, 'updated_at',        'Updated At',     150, 14, true, false, 'field'),
    (v_view_id, 'process_status',    'Status',         120, 15, true, true,  'field'),
    (v_view_id, 'start_time',         'Start Time',     160, 16, true, true,  'field'),
    (v_view_id, 'initiator',          'Initiator',      140, 17, true, true,  'field'),
    (v_view_id, 'current_step',       'Current Step',    140, 18, true, true,  'field');

    -- -----------------------------------------------------------------
    -- Forms
    -- -----------------------------------------------------------------
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, scene, display_name, bound_table_id,
        config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Inbound Case Form', 'PROCESS', 'REQUEST',
        'My Request view of the inbound email',
        v_main_table_id,
        $cfg${
          "rule": [
            {"name":"ref_eir_id","type":"input","field":"id","title":"Case ID","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_eir_id","_fc_drag_tag":"input"},
            {"name":"ref_eir_from","type":"input","field":"sender_email","title":"From","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_eir_from","_fc_drag_tag":"input"},
            {"name":"ref_eir_to","type":"input","field":"email_to","title":"To","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_eir_to","_fc_drag_tag":"input"},
            {"name":"ref_eir_subj","type":"input","field":"email_subject","title":"Subject","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_eir_subj","_fc_drag_tag":"input"},
            {"name":"ref_eir_body","type":"input","field":"email_body","title":"Body","props":{"type":"textarea","rows":8,"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_eir_body","_fc_drag_tag":"input"},
            {"name":"ref_eir_mid","type":"input","field":"message_id","title":"Message-ID","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_eir_mid","_fc_drag_tag":"input"},
            {"name":"ref_eir_att","type":"upload","field":"inbound_attachment","title":"Attachments","props":{"readonly":true,"limit":10,"listType":"text","action":"/api/v1/upload"},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_eir_att","_fc_drag_tag":"upload"},
            {"name":"ref_eir_need","type":"switch","field":"need_reply","title":"Need Reply","props":{},"hidden":false,"display":true,"_fc_id":"id_eir_need","_fc_drag_tag":"switch"},
            {"name":"ref_eir_rsubj","type":"input","field":"reply_subject","title":"Reply Subject","props":{},"hidden":false,"display":true,"_fc_id":"id_eir_rsubj","_fc_drag_tag":"input"},
            {"name":"ref_eir_rbody","type":"input","field":"reply_body","title":"Reply Body","props":{"type":"textarea","rows":6},"hidden":false,"display":true,"_fc_id":"id_eir_rbody","_fc_drag_tag":"input"}
          ],
          "options": {"form":{"size":"default","inline":false,"labelWidth":"140px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false},"submitBtn":{"show":false}},
          "subForms": {}
        }$cfg$::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_form_inbound_id;

    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, scene, display_name, bound_table_id,
        config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Review Inbound Form', 'TASK', 'TASK',
        'Review inbound email (read-only)',
        v_main_table_id,
        $cfg${
          "rule": [
            {"name":"ref_rev_card","type":"elCard","props":{"header":"Inbound Email"},"style":{"width":"100%"},"hidden":false,"display":true,"_fc_id":"id_rev_card","_fc_drag_tag":"elCard","children":[
              {"name":"ref_rev_id","type":"input","field":"id","title":"Case ID","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_rev_id","_fc_drag_tag":"input"},
              {"name":"ref_rev_from","type":"input","field":"sender_email","title":"From","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_rev_from","_fc_drag_tag":"input"},
              {"name":"ref_rev_to","type":"input","field":"email_to","title":"To","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_rev_to","_fc_drag_tag":"input"},
              {"name":"ref_rev_subj","type":"input","field":"email_subject","title":"Subject","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_rev_subj","_fc_drag_tag":"input"},
              {"name":"ref_rev_body","type":"input","field":"email_body","title":"Body","props":{"type":"textarea","rows":8,"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_rev_body","_fc_drag_tag":"input"},
              {"name":"ref_rev_mid","type":"input","field":"message_id","title":"Message-ID","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_rev_mid","_fc_drag_tag":"input"},
              {"name":"ref_rev_att","type":"upload","field":"inbound_attachment","title":"Attachments","props":{"readonly":true,"limit":10,"listType":"text","action":"/api/v1/upload"},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_rev_att","_fc_drag_tag":"upload"}
            ]}
          ],
          "options": {"form":{"size":"default","inline":false,"labelWidth":"140px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false},"submitBtn":{"show":false}},
          "subForms": {}
        }$cfg$::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_form_review_id;

    v_confirm_cfg := $cfg${
      "rule": [
        {"name":"ref_cfm_in","type":"elCard","props":{"header":"Inbound Email (read-only)"},"style":{"width":"100%"},"hidden":false,"display":true,"_fc_id":"id_cfm_in","_fc_drag_tag":"elCard","children":[
          {"name":"ref_cfm_id","type":"input","field":"id","title":"Case ID","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_cfm_id","_fc_drag_tag":"input"},
          {"name":"ref_cfm_from","type":"input","field":"sender_email","title":"From","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_cfm_from","_fc_drag_tag":"input"},
          {"name":"ref_cfm_subj","type":"input","field":"email_subject","title":"Subject","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_cfm_subj","_fc_drag_tag":"input"},
          {"name":"ref_cfm_body","type":"input","field":"email_body","title":"Body","props":{"type":"textarea","rows":6,"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_cfm_body","_fc_drag_tag":"input"},
          {"name":"ref_cfm_att","type":"upload","field":"inbound_attachment","title":"Attachments","props":{"readonly":true,"limit":10,"listType":"text","action":"/api/v1/upload"},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_cfm_att","_fc_drag_tag":"upload"}
        ]},
        {"name":"ref_cfm_out","type":"elCard","props":{"header":"Reply Draft"},"style":{"width":"100%"},"hidden":false,"display":true,"_fc_id":"id_cfm_out","_fc_drag_tag":"elCard","children":[
          {"name":"ref_cfm_need","type":"switch","field":"need_reply","title":"Need Reply","props":{},"hidden":false,"display":true,"_fc_id":"id_cfm_need","_fc_drag_tag":"switch"},
          {"name":"ref_cfm_rsubj","type":"input","field":"reply_subject","title":"Reply Subject","props":{"maxlength":500},"hidden":false,"display":true,"_fc_id":"id_cfm_rsubj","_fc_drag_tag":"input"},
          {"name":"ref_cfm_rbody","type":"input","field":"reply_body","title":"Reply Body","props":{"type":"textarea","rows":6},"hidden":false,"display":true,"_fc_id":"id_cfm_rbody","_fc_drag_tag":"input"}
        ]}
      ],
      "options": {
        "form":{"size":"default","inline":false,"labelWidth":"140px","labelPosition":"left","hideRequiredAsterisk":false},
        "resetBtn":{"show":false},
        "submitBtn":{"show":false},
        "onCreated": "[[FORM-CREATE-PREFIX-function onCreated(api){\n  if (!api.getValue('reply_subject') && api.getValue('email_subject')) {\n    api.setValue('reply_subject', api.getValue('email_subject'));\n  }\n  if (!api.getValue('reply_body') && api.getValue('email_body')) {\n    api.setValue('reply_body', api.getValue('email_body'));\n  }\n  if (api.getValue('need_reply') === undefined || api.getValue('need_reply') === null) {\n    api.setValue('need_reply', true);\n  }\n}-FORM-CREATE-SUFFIX]]"
      },
      "subForms": {}
    }$cfg$::jsonb;

    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, scene, display_name, bound_table_id,
        config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Confirm Send Form', 'TASK', 'TASK',
        'Confirm inbound mail and write the reply, then send',
        v_main_table_id, v_confirm_cfg, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_form_confirm_id;

    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, scene, display_name, bound_table_id,
        config_json, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Send Confirm Popup', 'ACTION', 'TASK',
        'Popup confirmation note only — inbound mail is on the host form',
        v_action_table_id,
        jsonb_build_object(
            'rule', '[]'::jsonb,
            'options', v_empty_opts::jsonb,
            'subForms', '{}'::jsonb
        ),
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_form_popup_id;

    -- -----------------------------------------------------------------
    -- Bindings
    -- -----------------------------------------------------------------
    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES
    (v_form_inbound_id, v_main_table_id,   'PRIMARY', 'READONLY', NULL,      1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (v_form_review_id,  v_main_table_id,   'PRIMARY', 'READONLY', NULL,      1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (v_form_confirm_id, v_main_table_id,   'PRIMARY', 'EDITABLE', NULL,      1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

    INSERT INTO dw_form_table_bindings (
        form_id, table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at
    ) VALUES (
        v_form_popup_id, v_action_table_id, 'ACTION', 'EDITABLE', 'main_id', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_bind_popup_id;

    v_popup_canvas := $cfg${
      "rule": [
        {"name":"ref_pop_note","type":"input","field":"confirm_note","title":"Confirm Note","props":{"type":"textarea","rows":4,"placeholder":"Check the inbound email on the main form, then add a confirmation note here."},"hidden":false,"display":true,"_fc_id":"id_pop_note","_fc_drag_tag":"input"}
      ],
      "options": {"form":{"size":"default","inline":false,"labelWidth":"140px","labelPosition":"left","hideRequiredAsterisk":false},"resetBtn":{"show":false},"submitBtn":{"show":true,"innerText":"Submit"}}
    }$cfg$::jsonb;

    UPDATE dw_form_definitions
    SET config_json = jsonb_set(config_json, '{subForms}', jsonb_build_object(v_bind_popup_id::text, v_popup_canvas))
    WHERE id = v_form_popup_id;

    INSERT INTO dw_form_stage_bindings (form_id, stage_id, stage_name, read_only)
    VALUES
    (v_form_review_id,  'Task_ReviewInbound', 'Review Inbound Form', true),
    (v_form_confirm_id, 'Task_ConfirmSend',   'Confirm Send Form', false),
    (v_form_confirm_id, 'Task_CheckerReview', 'Confirm Send Form', true);

    -- -----------------------------------------------------------------
    -- Actions (popup config needs formId)
    -- -----------------------------------------------------------------
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, display_name, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Save Draft', 'SAVE',
        '{"confirmMessage":"","requireComment":false}'::jsonb,
        NULL, NULL, 'Save draft without leaving the task', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_act_save_id;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, display_name, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Continue', 'APPROVE',
        '{"targetStatus":"REVIEWED","confirmMessage":"Continue to confirm and send?","requireComment":false}'::jsonb,
        'Check', 'success', 'Finish review and go to confirm', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_act_continue_id;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, display_name, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Preview & Confirm', 'FORM_POPUP',
        jsonb_build_object(
            'formId', v_form_popup_id,
            'formName', 'Send Confirm Popup',
            'readOnly', false,
            'popupTitle', 'Confirm send',
            'popupWidth', '640px',
            'dialogTitle', 'Confirm send',
            'dialogWidth', '640px',
            'allowedRoles', '[]'::jsonb,
            'visibilityCondition', NULL
        ),
        'ChatDotRound', 'primary', 'Write a confirmation note (does not send yet)', false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_act_popup_id;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, display_name, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Send Email', 'APPROVE',
        '{"targetStatus":"SENDING","confirmMessage":"Send the reply email now?","requireComment":false}'::jsonb,
        'Check', 'success', 'Send the reply immediately', false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_act_send_id;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, display_name, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Submit for Checker Review', 'REJECT',
        '{"targetStatus":"PENDING_CHECK","confirmMessage":"Send this case to Department Manager for review?","requireComment":false}'::jsonb,
        'User', 'warning', 'Department Manager reviews whether to send', false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_act_checker_id;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, display_name, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, 'Back to Confirm & Send', 'REJECT',
        '{"targetStatus":"RECONFIRM","confirmMessage":"Return this case to Confirm & Send?","requireComment":false}'::jsonb,
        'RefreshLeft', 'info', 'Return to Confirm & Send', false,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_act_reconfirm_id;

    -- -----------------------------------------------------------------
    -- Email connection / template / monitor (placeholders — set credentials in DW)
    -- -----------------------------------------------------------------
    INSERT INTO dw_email_connections (
        connection_uid, function_unit_id, name, connection_type, host, port,
        username, credential_encrypted, from_email, from_name, use_tls, enabled,
        direction, imap_host, imap_port, imap_use_ssl, created_at, updated_at
    ) VALUES
    (v_in_uid, v_fu_id, 'Inbound Mailbox', 'SMTP', 'imap.example.com', 993,
     'demo@example.com', NULL, 'demo@example.com', 'Email Inbound Reply', true, false,
     'INBOUND', 'imap.example.com', 993, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (v_out_uid, v_fu_id, 'Outbound Mailbox', 'SMTP', 'smtp.example.com', 587,
     'demo@example.com', NULL, 'demo@example.com', 'Email Inbound Reply', true, false,
     'OUTBOUND', NULL, NULL, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

    UPDATE dw_email_connections c
    SET credential_encrypted = k.credential_encrypted,
        username = COALESCE(NULLIF(k.username, ''), c.username),
        host = COALESCE(NULLIF(k.host, ''), c.host),
        port = COALESCE(k.port, c.port),
        from_email = COALESCE(NULLIF(k.from_email, ''), c.from_email),
        from_name = COALESCE(NULLIF(k.from_name, ''), c.from_name),
        use_tls = COALESCE(k.use_tls, c.use_tls),
        enabled = COALESCE(k.enabled, c.enabled),
        imap_host = COALESCE(NULLIF(k.imap_host, ''), c.imap_host),
        imap_port = COALESCE(k.imap_port, c.imap_port),
        imap_use_ssl = COALESCE(k.imap_use_ssl, c.imap_use_ssl),
        mailbox_address = COALESCE(NULLIF(k.mailbox_address, ''), c.mailbox_address)
    FROM tmp_eir_mail_keep k
    WHERE c.function_unit_id = v_fu_id
      AND c.name = k.name;

    -- Last Deploy copy (same connection_uid) survives DW delete; use it when keep is placeholder.
    UPDATE dw_email_connections c
    SET credential_encrypted = s.credential_encrypted,
        username = COALESCE(NULLIF(s.username, ''), c.username),
        host = CASE
            WHEN c.direction = 'INBOUND' THEN COALESCE(NULLIF(s.host, ''), NULLIF(s.imap_host, ''), c.host)
            ELSE COALESCE(NULLIF(s.host, ''), c.host)
        END,
        port = CASE
            WHEN c.direction = 'INBOUND' THEN COALESCE(s.imap_port, s.port, c.port)
            ELSE COALESCE(s.port, c.port)
        END,
        from_email = COALESCE(NULLIF(s.from_email, ''), c.from_email),
        from_name = COALESCE(NULLIF(s.from_name, ''), c.from_name),
        use_tls = COALESCE(s.use_tls, c.use_tls),
        enabled = COALESCE(s.enabled, c.enabled),
        imap_host = COALESCE(NULLIF(s.imap_host, ''), c.imap_host),
        imap_port = COALESCE(s.imap_port, c.imap_port),
        imap_use_ssl = COALESCE(s.imap_use_ssl, c.imap_use_ssl),
        mailbox_address = COALESCE(NULLIF(s.mailbox_address, ''), c.mailbox_address)
    FROM sys_email_connections s
    WHERE c.connection_uid = s.id
      AND c.function_unit_id = v_fu_id
      AND s.credential_encrypted IS NOT NULL
      AND length(s.credential_encrypted) > 0;

    INSERT INTO dw_email_templates (
        function_unit_id, name, subject, body_html, enabled, created_at, updated_at
    ) VALUES (
        v_fu_id,
        'Inbound Reply Template',
        '${reply_subject}',
        $html$<!DOCTYPE html>
<html>
<head><meta charset="UTF-8" /><title>Reply</title></head>
<body>
  <p>您好，</p>
  <p>我们已收到您的邮件（主题：<strong>${email_subject}</strong>，案号：<strong>${id}</strong>）。</p>
  <p>回复如下：</p>
  <div>${reply_body}</div>
  <hr />
  <p style="color:#666;font-size:12px;">--- 原信摘要 ---</p>
  <p style="color:#666;font-size:12px;">发件人：${sender_email}</p>
  <pre style="color:#666;font-size:12px;white-space:pre-wrap;">${email_body}</pre>
</body>
</html>$html$,
        true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_tpl_id;

    v_extraction := $ext${
      "fields": [
        {"source":"FROM","target":"sender_email","type":"DIRECT","required":true},
        {"source":"TO","target":"email_to","type":"DIRECT","required":false},
        {"source":"SUBJECT","target":"email_subject","type":"DIRECT","required":true},
        {"source":"SUBJECT","target":"reply_subject","type":"DIRECT","required":false},
        {"source":"TEXT_AND_HTML","target":"email_body","type":"DIRECT","required":false},
        {"source":"TEXT_AND_HTML","target":"reply_body","type":"DIRECT","required":false},
        {"source":"MESSAGE_ID","target":"message_id","type":"DIRECT","required":false},
        {"source":"DATE","target":"received_at","type":"DIRECT","required":false},
        {"source":"ATTACHMENTS","target":"inbound_attachment","type":"DIRECT","required":false}
      ],
      "subTables": []
    }$ext$::jsonb;

    INSERT INTO dw_email_monitor_rules (
        rule_uid, function_unit_id, name, enabled, connection_uid,
        folder_label, action_type, system_initiator_user_id,
        extraction_rules, poll_interval_seconds, review_on_missing, created_at, updated_at
    ) VALUES (
        v_tpl_rule_uid, v_fu_id, 'Start from inbound email', true, v_in_uid,
        'INBOX', 'START_PROCESS', 'user-dev',
        v_extraction, 60, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_monitor_tpl_id;

    INSERT INTO dw_email_monitor_rules (
        rule_uid, function_unit_id, name, enabled, connection_uid, process_definition_key,
        start_event_id, source_rule_id, folder_label, action_type, system_initiator_user_id,
        extraction_rules, poll_interval_seconds, review_on_missing, created_at, updated_at
    ) VALUES (
        v_rule_uid, v_fu_id, 'Start from inbound email → StartEvent_1', true, v_in_uid,
        'Process_EmailInboundReply', 'StartEvent_1', v_monitor_tpl_id, 'INBOX', 'START_PROCESS', 'user-dev',
        v_extraction, 60, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    -- -----------------------------------------------------------------
    -- BPMN
    -- -----------------------------------------------------------------
    v_bpmn_xml := format($xml$<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:custom="http://workflow.platform/schema/custom" xmlns:flowable="http://flowable.org/bpmn" id="Definitions_EmailInboundReply" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_EmailInboundReply" name="Email Inbound Reply" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Email Received">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="%s" />
          <custom:property name="formName" value="Inbound Case Form" />
          <custom:property name="emailMonitorRuleId" value="%s" />
          <custom:property name="emailMonitorEnabled" value="true" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:userTask id="Task_ReviewInbound" name="Review Inbound">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="assigneeType" value="BU_ROLE" />
          <custom:property name="assigneeLabel" value="Fixed BU + role: Department Manager" />
          <custom:property name="formId" value="%s" />
          <custom:property name="formName" value="Review Inbound Form" />
          <custom:property name="formReadOnly" value="true" />
          <custom:property name="actionIds" value="[%s,%s]" />
          <custom:property name="actionNames" value="[&amp;#34;Save Draft&amp;#34;,&amp;#34;Continue&amp;#34;]" />
          <custom:property name="roleId" value="MANAGER" />
          <custom:property name="roleIds" value="MANAGER" />
          <custom:property name="businessUnitId" value="E2E_FINANCE" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:userTask id="Task_ConfirmSend" name="Confirm &amp; Send">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="assigneeType" value="BU_ROLE" />
          <custom:property name="assigneeLabel" value="Fixed BU + role: Department Manager" />
          <custom:property name="formId" value="%s" />
          <custom:property name="formName" value="Confirm Send Form" />
          <custom:property name="actionIds" value="[%s,%s,%s]" />
          <custom:property name="actionNames" value="[&amp;#34;Save Draft&amp;#34;,&amp;#34;Submit for Checker Review&amp;#34;,&amp;#34;Send Email&amp;#34;]" />
          <custom:property name="roleId" value="MANAGER" />
          <custom:property name="roleIds" value="MANAGER" />
          <custom:property name="businessUnitId" value="E2E_FINANCE" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:incoming>Flow_CheckerBack</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_ConfirmRoute" name="Send now?" default="Flow_ToChecker">
      <bpmn:incoming>Flow_3</bpmn:incoming>
      <bpmn:outgoing>Flow_Send</bpmn:outgoing>
      <bpmn:outgoing>Flow_ToChecker</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:userTask id="Task_CheckerReview" name="Checker Review">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="assigneeType" value="FIXED_BU_ROLE" />
          <custom:property name="assigneeLabel" value="Fixed BU Role: Department Manager" />
          <custom:property name="roleId" value="MANAGER" />
          <custom:property name="roleIds" value="MANAGER" />
          <custom:property name="formId" value="%s" />
          <custom:property name="formName" value="Confirm Send Form" />
          <custom:property name="formReadOnly" value="true" />
          <custom:property name="actionIds" value="[%s,%s]" />
          <custom:property name="actionNames" value="[&amp;#34;Back to Confirm &amp; Send&amp;#34;,&amp;#34;Send Email&amp;#34;]" />
          <custom:property name="businessUnitId" value="E2E_FINANCE" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_ToChecker</bpmn:incoming>
      <bpmn:outgoing>Flow_CheckerOut</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:exclusiveGateway id="Gateway_CheckerRoute" name="Checker send?" default="Flow_CheckerBack">
      <bpmn:incoming>Flow_CheckerOut</bpmn:incoming>
      <bpmn:outgoing>Flow_CheckerSend</bpmn:outgoing>
      <bpmn:outgoing>Flow_CheckerBack</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:sendTask id="Task_SendReply" name="Send Reply Email">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="connectionId" value="%s" />
          <custom:property name="emailTo" value="${sender_email}" />
          <custom:property name="emailTemplateId" value="%s" />
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_Send</bpmn:incoming>
      <bpmn:incoming>Flow_CheckerSend</bpmn:incoming>
      <bpmn:outgoing>Flow_4</bpmn:outgoing>
    </bpmn:sendTask>
    <bpmn:endEvent id="EndEvent_Sent" name="Sent">
      <bpmn:incoming>Flow_4</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_ReviewInbound" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_ReviewInbound" targetRef="Task_ConfirmSend" />
    <bpmn:sequenceFlow id="Flow_3" sourceRef="Task_ConfirmSend" targetRef="Gateway_ConfirmRoute" />
    <bpmn:sequenceFlow id="Flow_Send" name="send" sourceRef="Gateway_ConfirmRoute" targetRef="Task_SendReply">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${execution.getVariable('decision') == 'yes'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_ToChecker" name="checker" sourceRef="Gateway_ConfirmRoute" targetRef="Task_CheckerReview" />
    <bpmn:sequenceFlow id="Flow_CheckerOut" sourceRef="Task_CheckerReview" targetRef="Gateway_CheckerRoute" />
    <bpmn:sequenceFlow id="Flow_CheckerSend" name="send" sourceRef="Gateway_CheckerRoute" targetRef="Task_SendReply">
      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${execution.getVariable('decision') == 'yes'}</bpmn:conditionExpression>
    </bpmn:sequenceFlow>
    <bpmn:sequenceFlow id="Flow_CheckerBack" name="reconfirm" sourceRef="Gateway_CheckerRoute" targetRef="Task_ConfirmSend" />
    <bpmn:sequenceFlow id="Flow_4" sourceRef="Task_SendReply" targetRef="EndEvent_Sent" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_EmailInboundReply">
      <bpmndi:BPMNShape id="Shape_Start" bpmnElement="StartEvent_1"><dc:Bounds x="162" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Review" bpmnElement="Task_ReviewInbound"><dc:Bounds x="250" y="80" width="120" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Confirm" bpmnElement="Task_ConfirmSend"><dc:Bounds x="430" y="80" width="120" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Gw" bpmnElement="Gateway_ConfirmRoute" isMarkerVisible="true"><dc:Bounds x="605" y="95" width="50" height="50" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Send" bpmnElement="Task_SendReply"><dc:Bounds x="800" y="80" width="120" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_EndSent" bpmnElement="EndEvent_Sent"><dc:Bounds x="972" y="102" width="36" height="36" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_Checker" bpmnElement="Task_CheckerReview"><dc:Bounds x="570" y="220" width="120" height="80" /></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Shape_GwChecker" bpmnElement="Gateway_CheckerRoute" isMarkerVisible="true"><dc:Bounds x="745" y="235" width="50" height="50" /></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Edge_1" bpmnElement="Flow_1"><di:waypoint x="198" y="120" /><di:waypoint x="250" y="120" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_2" bpmnElement="Flow_2"><di:waypoint x="370" y="120" /><di:waypoint x="430" y="120" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_3" bpmnElement="Flow_3"><di:waypoint x="550" y="120" /><di:waypoint x="605" y="120" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_Send" bpmnElement="Flow_Send"><di:waypoint x="655" y="120" /><di:waypoint x="800" y="120" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_ToChecker" bpmnElement="Flow_ToChecker"><di:waypoint x="630" y="145" /><di:waypoint x="630" y="220" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_CheckerOut" bpmnElement="Flow_CheckerOut"><di:waypoint x="690" y="260" /><di:waypoint x="745" y="260" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_CheckerSend" bpmnElement="Flow_CheckerSend"><di:waypoint x="795" y="260" /><di:waypoint x="860" y="260" /><di:waypoint x="860" y="160" /><di:waypoint x="860" y="120" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_CheckerBack" bpmnElement="Flow_CheckerBack"><di:waypoint x="745" y="260" /><di:waypoint x="490" y="260" /><di:waypoint x="490" y="160" /></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="Edge_4" bpmnElement="Flow_4"><di:waypoint x="920" y="120" /><di:waypoint x="972" y="120" /></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
$xml$,
        v_form_inbound_id,
        (SELECT id::text FROM dw_email_monitor_rules WHERE rule_uid = v_rule_uid),
        v_form_review_id,
        v_act_save_id,
        v_act_continue_id,
        v_form_confirm_id,
        v_act_save_id,
        v_act_checker_id,
        v_act_send_id,
        v_form_confirm_id,
        v_act_reconfirm_id,
        v_act_send_id,
        v_out_uid,
        v_tpl_id
    );

    v_bpmn_b64 := encode(convert_to(v_bpmn_xml, 'UTF8'), 'base64');

    INSERT INTO dw_process_definitions (
        function_unit_id, function_unit_version_id, bpmn_xml, created_at, updated_at
    ) VALUES (
        v_fu_id, v_fu_id, v_bpmn_b64, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    );

    RAISE NOTICE '========================================';
    RAISE NOTICE 'Email Inbound Reply seeded';
    RAISE NOTICE 'FU id=% code=fu-20260910-emlrep', v_fu_id;
    RAISE NOTICE 'Tables main=% action=%', v_main_table_id, v_action_table_id;
    RAISE NOTICE 'Forms inbound=% review=% confirm=% popup=%', v_form_inbound_id, v_form_review_id, v_form_confirm_id, v_form_popup_id;
    RAISE NOTICE 'Actions save=% continue=% send=% checker=% reconfirm=%', v_act_save_id, v_act_continue_id, v_act_send_id, v_act_checker_id, v_act_reconfirm_id;
    RAISE NOTICE 'Email template=% outboundConn=%', v_tpl_id, v_out_uid;
    RAISE NOTICE 'View id=% (Inbound Email Case)', v_view_id;
    RAISE NOTICE 'Seed version=1.0.5 status=DRAFT. Set mailbox credentials in DW if empty, then Publish and Deploy.';
    RAISE NOTICE '========================================';
END
$main$;

COMMIT;
