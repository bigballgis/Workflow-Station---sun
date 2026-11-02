-- Post-seed: Email Inbound Reply rich HTML body and reply editor.
-- Idempotent. No-op when fu-20260910-emlrep is absent or already migrated.
-- The historical seed 20-email-inbound-reply/init.sql stays unchanged.

BEGIN;

UPDATE dw_form_definitions AS f
SET scene = 'TASK',
    updated_at = CURRENT_TIMESTAMP
FROM dw_function_units AS fu
WHERE f.function_unit_id = fu.id
  AND fu.code = 'fu-20260910-emlrep'
  AND f.form_name = 'Inbound Case Form'
  AND f.scene IS DISTINCT FROM 'TASK';

UPDATE dw_form_definitions AS f
SET config_json = replace(
      replace(
        replace(
          replace(
            replace(
              replace(f.config_json::text,
                '{"name":"ref_eir_body","type":"input","field":"email_body","title":"Body","props":{"type":"textarea","rows":8,"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_eir_body","_fc_drag_tag":"input"}',
                '{"name":"ref_eir_body","type":"editor","field":"email_body","title":"Body","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_eir_body","_fc_drag_tag":"editor"}'),
              '{"name":"ref_rev_body","type":"input","field":"email_body","title":"Body","props":{"type":"textarea","rows":8,"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_rev_body","_fc_drag_tag":"input"}',
              '{"name":"ref_rev_body","type":"editor","field":"email_body","title":"Body","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_rev_body","_fc_drag_tag":"editor"}'),
            '{"name":"ref_cfm_body","type":"input","field":"email_body","title":"Body","props":{"type":"textarea","rows":6,"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_cfm_body","_fc_drag_tag":"input"}',
            '{"name":"ref_cfm_body","type":"editor","field":"email_body","title":"Body","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_cfm_body","_fc_drag_tag":"editor"}'),
          '{"name":"ref_eir_rbody","type":"input","field":"reply_body","title":"Reply Body","props":{"type":"textarea","rows":6},"hidden":false,"display":true,"_fc_id":"id_eir_rbody","_fc_drag_tag":"input"}',
          '{"name":"ref_eir_rbody","type":"editor","field":"reply_body","title":"Reply Body","props":{"readonly":false},"readonly":false,"hidden":false,"display":true,"_fc_id":"id_eir_rbody","_fc_drag_tag":"editor"}'),
        '{"name":"ref_cfm_rbody","type":"input","field":"reply_body","title":"Reply Body","props":{"type":"textarea","rows":6},"hidden":false,"display":true,"_fc_id":"id_cfm_rbody","_fc_drag_tag":"input"}',
        '{"name":"ref_cfm_rbody","type":"editor","field":"reply_body","title":"Reply Body","props":{"readonly":false},"readonly":false,"hidden":false,"display":true,"_fc_id":"id_cfm_rbody","_fc_drag_tag":"editor"}'),
      $old$  if (!api.getValue('reply_body') && api.getValue('email_body')) {\n    api.setValue('reply_body', api.getValue('email_body'));\n  }\n$old$,
      '')::jsonb,
    updated_at = CURRENT_TIMESTAMP
FROM dw_function_units AS fu
WHERE f.function_unit_id = fu.id
  AND fu.code = 'fu-20260910-emlrep'
  AND (
    f.config_json::text LIKE '%"field":"email_body","title":"Body","props":{"type":"textarea"%'
    OR f.config_json::text LIKE '%"field":"reply_body","title":"Reply Body","props":{"type":"textarea"%'
    OR f.config_json::text LIKE '%setValue(''reply_body'', api.getValue(''email_body''))%'
  );

UPDATE dw_email_templates AS t
SET body_html = replace(
      replace(t.body_html,
        '<div>${reply_body}</div>',
        '<div>${richText:reply_body}</div>'),
      '<pre style="color:#666;font-size:12px;white-space:pre-wrap;">${email_body}</pre>',
      '<div>${richText:email_body}</div>'),
    updated_at = CURRENT_TIMESTAMP
FROM dw_function_units AS fu
WHERE t.function_unit_id = fu.id
  AND fu.code = 'fu-20260910-emlrep'
  AND (
    t.body_html LIKE '%${reply_body}%'
    OR t.body_html LIKE '%<pre style="color:#666;font-size:12px;white-space:pre-wrap;">${email_body}</pre>%'
  );

UPDATE dw_email_monitor_rules AS r
SET extraction_rules = replace(
      replace(r.extraction_rules::text,
        '{"source":"TEXT_AND_HTML","target":"email_body"',
        '{"source":"HTML_RICH","target":"email_body"'),
      '{"source":"TEXT_AND_HTML","target":"reply_body"',
      '{"source":"HTML_RICH","target":"reply_body"')::jsonb,
    updated_at = CURRENT_TIMESTAMP
FROM dw_function_units AS fu
WHERE r.function_unit_id = fu.id
  AND fu.code = 'fu-20260910-emlrep'
  AND r.extraction_rules::text LIKE '%TEXT_AND_HTML%';

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'protect_released_fu_content') THEN
    EXECUTE 'ALTER TABLE sys_function_unit_contents DISABLE TRIGGER protect_released_fu_content';
  END IF;
END $$;

UPDATE sys_function_unit_contents AS c
SET content_data = replace(
      replace(c.content_data,
        '"scene":"REQUEST"', '"scene":"TASK"'),
      '"scene": "REQUEST"', '"scene": "TASK"')
FROM sys_function_units AS fu
WHERE c.function_unit_id = fu.id
  AND fu.code = 'fu-20260910-emlrep'
  AND c.content_name = 'Inbound Case Form'
  AND c.content_data LIKE '%REQUEST%';

UPDATE sys_function_unit_contents AS c
SET content_data = replace(c.content_data,
      '{"name":"ref_eir_body","type":"input","field":"email_body","title":"Body","props":{"type":"textarea","rows":8,"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_eir_body","_fc_drag_tag":"input"}',
      '{"name":"ref_eir_body","type":"editor","field":"email_body","title":"Body","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_eir_body","_fc_drag_tag":"editor"}')
FROM sys_function_units AS fu
WHERE c.function_unit_id = fu.id AND fu.code = 'fu-20260910-emlrep'
  AND c.content_data LIKE '%"ref_eir_body","type":"input"%';

UPDATE sys_function_unit_contents AS c
SET content_data = replace(c.content_data,
      '{"name":"ref_rev_body","type":"input","field":"email_body","title":"Body","props":{"type":"textarea","rows":8,"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_rev_body","_fc_drag_tag":"input"}',
      '{"name":"ref_rev_body","type":"editor","field":"email_body","title":"Body","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_rev_body","_fc_drag_tag":"editor"}')
FROM sys_function_units AS fu
WHERE c.function_unit_id = fu.id AND fu.code = 'fu-20260910-emlrep'
  AND c.content_data LIKE '%"ref_rev_body","type":"input"%';

UPDATE sys_function_unit_contents AS c
SET content_data = replace(c.content_data,
      '{"name":"ref_cfm_body","type":"input","field":"email_body","title":"Body","props":{"type":"textarea","rows":6,"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_cfm_body","_fc_drag_tag":"input"}',
      '{"name":"ref_cfm_body","type":"editor","field":"email_body","title":"Body","props":{"readonly":true},"readonly":true,"hidden":false,"display":true,"_fc_id":"id_cfm_body","_fc_drag_tag":"editor"}')
FROM sys_function_units AS fu
WHERE c.function_unit_id = fu.id AND fu.code = 'fu-20260910-emlrep'
  AND c.content_data LIKE '%"ref_cfm_body","type":"input"%';

UPDATE sys_function_unit_contents AS c
SET content_data = replace(c.content_data,
      '{"name":"ref_eir_rbody","type":"input","field":"reply_body","title":"Reply Body","props":{"type":"textarea","rows":6},"hidden":false,"display":true,"_fc_id":"id_eir_rbody","_fc_drag_tag":"input"}',
      '{"name":"ref_eir_rbody","type":"editor","field":"reply_body","title":"Reply Body","props":{"readonly":false},"readonly":false,"hidden":false,"display":true,"_fc_id":"id_eir_rbody","_fc_drag_tag":"editor"}')
FROM sys_function_units AS fu
WHERE c.function_unit_id = fu.id AND fu.code = 'fu-20260910-emlrep'
  AND c.content_data LIKE '%{"name":"ref_eir_rbody","type":"input","field":"reply_body","title":"Reply Body","props":{"type":"textarea","rows":6}%';

UPDATE sys_function_unit_contents AS c
SET content_data = replace(c.content_data,
      'ref_eir_rbody","type":"input","field":"reply_body","props":{"rows":6,"type":"textarea"},"title":"Reply Body","_fc_id":"id_eir_rbody","hidden":false,"display":true,"_fc_drag_tag":"input"',
      'ref_eir_rbody","type":"editor","field":"reply_body","props":{"readonly":false},"title":"Reply Body","_fc_id":"id_eir_rbody","hidden":false,"display":true,"readonly":false,"_fc_drag_tag":"editor"')
FROM sys_function_units AS fu
WHERE c.function_unit_id = fu.id AND fu.code = 'fu-20260910-emlrep'
  AND c.content_data LIKE '%ref_eir_rbody","type":"input","field":"reply_body","props":{"rows":6,"type":"textarea"}%';

UPDATE sys_function_unit_contents AS c
SET content_data = replace(c.content_data,
      '{"name":"ref_cfm_rbody","type":"input","field":"reply_body","title":"Reply Body","props":{"type":"textarea","rows":6},"hidden":false,"display":true,"_fc_id":"id_cfm_rbody","_fc_drag_tag":"input"}',
      '{"name":"ref_cfm_rbody","type":"editor","field":"reply_body","title":"Reply Body","props":{"readonly":false},"readonly":false,"hidden":false,"display":true,"_fc_id":"id_cfm_rbody","_fc_drag_tag":"editor"}')
FROM sys_function_units AS fu
WHERE c.function_unit_id = fu.id AND fu.code = 'fu-20260910-emlrep'
  AND c.content_data LIKE '%"ref_cfm_rbody","type":"input"%';

UPDATE sys_function_unit_contents AS c
SET content_data = replace(
      replace(c.content_data,
        '<div>${reply_body}</div>',
        '<div>${richText:reply_body}</div>'),
      '<pre style="color:#666;font-size:12px;white-space:pre-wrap;">${email_body}</pre>',
      '<div>${richText:email_body}</div>')
FROM sys_function_units AS fu
WHERE c.function_unit_id = fu.id AND fu.code = 'fu-20260910-emlrep'
  AND (c.content_data LIKE '%${reply_body}%' OR c.content_data LIKE '%<pre style="color:#666;font-size:12px;white-space:pre-wrap;">${email_body}</pre>%');

UPDATE sys_function_unit_contents AS c
SET content_data = replace(
      replace(c.content_data,
        '{"source":"TEXT_AND_HTML","target":"email_body"',
        '{"source":"HTML_RICH","target":"email_body"'),
      '{"source":"TEXT_AND_HTML","target":"reply_body"',
      '{"source":"HTML_RICH","target":"reply_body"')
FROM sys_function_units AS fu
WHERE c.function_unit_id = fu.id AND fu.code = 'fu-20260910-emlrep'
  AND c.content_data LIKE '%TEXT_AND_HTML%';

UPDATE sys_email_monitor_rules AS r
SET extraction_rules = replace(
      replace(
        replace(
          replace(r.extraction_rules::text,
            '{"source":"TEXT_AND_HTML","target":"email_body"',
            '{"source":"HTML_RICH","target":"email_body"'),
          '{"source":"TEXT_AND_HTML","target":"reply_body"',
          '{"source":"HTML_RICH","target":"reply_body"'),
        '"source": "TEXT_AND_HTML", "target": "email_body"',
        '"source": "HTML_RICH", "target": "email_body"'),
      '"source": "TEXT_AND_HTML", "target": "reply_body"',
      '"source": "HTML_RICH", "target": "reply_body"')::jsonb
FROM sys_function_units AS fu
WHERE r.function_unit_id = fu.id
  AND fu.code = 'fu-20260910-emlrep'
  AND r.extraction_rules::text LIKE '%TEXT_AND_HTML%';

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'protect_released_fu_content') THEN
    EXECUTE 'ALTER TABLE sys_function_unit_contents ENABLE TRIGGER protect_released_fu_content';
  END IF;
END $$;

COMMIT;
