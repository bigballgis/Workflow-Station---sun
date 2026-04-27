-- =============================================================================
-- Patch: Add SAVE action to Function Unit "kk" (developer catalog)
--
-- What it does:
--   1) Locate target function unit by code='kk' OR name='kk' in dw_function_units
--   2) Insert a SAVE action definition if missing
--   3) Append the SAVE action ID to every userTask's actionIds in dw_process_definitions.bpmn_xml
--
-- Notes:
--   - This patch targets dw_* tables (developer-workstation source-of-truth in dev).
--   - It does not touch sys_action_definitions (deployed catalog).
--   - kk's dw_process_definitions.bpmn_xml may be Base64; this patch detects and
--     edits the decoded XML, then writes back in the original encoding.
-- =============================================================================

DO $patch$
DECLARE
  v_fu_id        BIGINT;
  v_save_action  BIGINT;
  v_bpmn_xml_raw TEXT;
  v_bpmn_xml     TEXT;
  v_new_bpmn_xml TEXT;
  v_was_base64   BOOLEAN;
BEGIN
  -- 1) Resolve Function Unit
  SELECT id INTO v_fu_id
  FROM dw_function_units
  WHERE code = 'kk' OR name = 'kk'
  ORDER BY id DESC
  LIMIT 1;

  IF v_fu_id IS NULL THEN
    RAISE EXCEPTION 'Function unit "kk" not found in dw_function_units (by code/name).';
  END IF;

  -- 2) Ensure SAVE action exists
  SELECT id INTO v_save_action
  FROM dw_action_definitions
  WHERE function_unit_id = v_fu_id
    AND (action_type = 'SAVE' OR action_name IN ('保存', '保存草稿', 'Save', 'Save Draft'))
  ORDER BY id DESC
  LIMIT 1;

  IF v_save_action IS NULL THEN
    INSERT INTO dw_action_definitions (
      function_unit_id, action_name, action_type, config_json,
      icon, button_color, description, is_default,
      created_at, updated_at
    ) VALUES (
      v_fu_id,
      '保存',
      'SAVE',
      '{}'::jsonb,
      'save',
      'primary',
      'Save current form without completing the task',
      true,
      CURRENT_TIMESTAMP,
      CURRENT_TIMESTAMP
    )
    RETURNING id INTO v_save_action;
  END IF;

  -- 3) Patch BPMN XML: append SAVE action ID to all userTask actionIds arrays
  SELECT bpmn_xml INTO v_bpmn_xml_raw
  FROM dw_process_definitions
  WHERE function_unit_id = v_fu_id
  ORDER BY updated_at DESC
  LIMIT 1;

  IF v_bpmn_xml_raw IS NULL OR length(trim(v_bpmn_xml_raw)) = 0 THEN
    RAISE NOTICE 'No dw_process_definitions.bpmn_xml found for fu_id=%, only inserted SAVE action (id=%).', v_fu_id, v_save_action;
    RETURN;
  END IF;

  -- Detect Base64 storage (common prefix for '<?xml' => 'PD94bWw=' / 'PD94bWwg')
  v_was_base64 := v_bpmn_xml_raw ~ '^PD94bWw';
  IF v_was_base64 THEN
    v_bpmn_xml := convert_from(decode(v_bpmn_xml_raw, 'base64'), 'UTF8');
  ELSE
    v_bpmn_xml := v_bpmn_xml_raw;
  END IF;

  -- Skip if the decoded XML already contains this action id as a standalone token
  IF v_bpmn_xml ~ ('(^|[^0-9])' || v_save_action::TEXT || '([^0-9]|$)') THEN
    RAISE NOTICE 'BPMN already contains SAVE action id %, skipping BPMN patch (fu_id=%).', v_save_action, v_fu_id;
    RETURN;
  END IF;

  -- Append `,<saveId>` before the closing bracket of actionIds value.
  -- kk BPMN uses: <custom:property name="actionIds" value="[45]" />
  -- We patch in two passes to avoid complex alternation regex pitfalls.
  v_new_bpmn_xml := v_bpmn_xml;

  -- Pass 1: name="actionIds" ... value="[...]" (name before value)
  v_new_bpmn_xml := regexp_replace(
    v_new_bpmn_xml,
    E'(name\\s*=\\s*["'']actionIds["''][^>]*?value\\s*=\\s*["'']\\[)([^\\]]*)(\\]["''])',
    E'\\1\\2,' || v_save_action::TEXT || E'\\3',
    'g'
  );

  -- Pass 2: value="[...]" ... name="actionIds" (value before name)
  v_new_bpmn_xml := regexp_replace(
    v_new_bpmn_xml,
    E'(value\\s*=\\s*["'']\\[)([^\\]]*)(\\]["''][^>]*?name\\s*=\\s*["'']actionIds["''])',
    E'\\1\\2,' || v_save_action::TEXT || E'\\3',
    'g'
  );

  IF v_new_bpmn_xml = v_bpmn_xml THEN
    RAISE NOTICE 'No actionIds pattern matched in BPMN; action inserted (id=%) but BPMN not modified (fu_id=%).', v_save_action, v_fu_id;
    RETURN;
  END IF;

  -- Re-encode to original storage format
  IF v_was_base64 THEN
    v_new_bpmn_xml := encode(convert_to(v_new_bpmn_xml, 'UTF8'), 'base64');
  END IF;

  UPDATE dw_process_definitions
  SET bpmn_xml = v_new_bpmn_xml,
      updated_at = CURRENT_TIMESTAMP
  WHERE function_unit_id = v_fu_id;

  RAISE NOTICE 'Patched FU kk (fu_id=%): SAVE action id=% inserted/ensured, BPMN actionIds updated.', v_fu_id, v_save_action;
END $patch$;

