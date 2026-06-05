-- =============================================================================
-- Patch: Add configured multi-instance progress fields to Function Unit "Multi-Instance Subtask Demo"
--
-- Why:
--   My Request must render subtask progress from developer-workstation metadata,
--   not from user-portal-only synthetic columns.
--
-- Storage note:
--   Subtask rows for this function unit are persisted as JSON (form / process variables), not as
--   a standalone physical relation table named public.subtable. This script only
--   updates dw_field_definitions and dw_form_definitions.config_json so the
--   designer and portal stay aligned; no ALTER TABLE is applied here.
-- =============================================================================

DO $patch$
DECLARE
  v_fu_id BIGINT;
  v_subtable_id BIGINT;
  v_form RECORD;
  v_binding RECORD;
  v_config JSONB;
  v_cols JSONB;
BEGIN
  SELECT id INTO v_fu_id
  FROM dw_function_units
  WHERE code = 'fu-20260422-23tfag'
     OR name IN ('Multi-Instance Subtask Demo', 'kk')
  ORDER BY id DESC
  LIMIT 1;

  IF v_fu_id IS NULL THEN
    RAISE EXCEPTION 'Function unit fu-20260422-23tfag (Multi-Instance Subtask Demo) not found in dw_function_units.';
  END IF;

  SELECT id INTO v_subtable_id
  FROM dw_table_definitions
  WHERE function_unit_id = v_fu_id
    AND table_name = 'subtable'
  ORDER BY id DESC
  LIMIT 1;

  IF v_subtable_id IS NULL THEN
    RAISE EXCEPTION 'Multi-Instance Subtask Demo subtable definition not found.';
  END IF;

  INSERT INTO dw_field_definitions (
    table_id, field_name, data_type, length, precision_value, scale,
    nullable, default_value, is_primary_key, is_unique, display_name, sort_order
  ) VALUES
    (v_subtable_id, 'task_status', 'VARCHAR', 20, NULL, NULL, false, '''PENDING''', false, false, 'Multi-instance subtask status', 3),
    (v_subtable_id, 'task_current_node', 'VARCHAR', 255, NULL, NULL, true, NULL, false, false, 'Current node inside the multi-instance subtask flow', 4)
  ON CONFLICT (table_id, field_name) DO UPDATE SET
    data_type = EXCLUDED.data_type,
    length = EXCLUDED.length,
    nullable = EXCLUDED.nullable,
    default_value = EXCLUDED.default_value,
    display_name = EXCLUDED.display_name,
    sort_order = EXCLUDED.sort_order;

  FOR v_form IN
    SELECT id, config_json
    FROM dw_form_definitions
    WHERE function_unit_id = v_fu_id
  LOOP
    v_config := COALESCE(v_form.config_json, '{}'::jsonb);
    v_config := jsonb_set(v_config, '{subListViews}', COALESCE(v_config->'subListViews', '{}'::jsonb), true);

    FOR v_binding IN
      SELECT id
      FROM dw_form_table_bindings
      WHERE form_id = v_form.id
        AND table_id = v_subtable_id
        AND binding_type = 'SUB'
    LOOP
      v_config := jsonb_set(
        v_config,
        ARRAY['subListViews', v_binding.id::text],
        COALESCE(v_config #> ARRAY['subListViews', v_binding.id::text], '{"columns":[]}'::jsonb),
        true
      );

      v_cols := COALESCE(v_config #> ARRAY['subListViews', v_binding.id::text, 'columns'], '[]'::jsonb);

      IF NOT EXISTS (
        SELECT 1 FROM jsonb_array_elements(v_cols) AS col
        WHERE col->>'fieldName' = 'task_status'
      ) THEN
        v_cols := v_cols || jsonb_build_array(jsonb_build_object(
          'comment', 'Status',
          'dataType', 'VARCHAR',
          'fieldName', 'task_status',
          'columnType', 'field',
          'columnLabel', 'Status'
        ));
      END IF;

      IF NOT EXISTS (
        SELECT 1 FROM jsonb_array_elements(v_cols) AS col
        WHERE col->>'fieldName' = 'task_current_node'
      ) THEN
        v_cols := v_cols || jsonb_build_array(jsonb_build_object(
          'comment', 'Current Step',
          'dataType', 'VARCHAR',
          'fieldName', 'task_current_node',
          'columnType', 'field',
          'columnLabel', 'Current Step'
        ));
      END IF;

      v_config := jsonb_set(
        v_config,
        ARRAY['subListViews', v_binding.id::text, 'columns'],
        v_cols,
        true
      );
    END LOOP;

    UPDATE dw_form_definitions
    SET config_json = v_config,
        updated_at = CURRENT_TIMESTAMP
    WHERE id = v_form.id;
  END LOOP;

  RAISE NOTICE 'Patched FU Multi-Instance Subtask Demo (fu_id=%): configured task_status/task_current_node for subtable.', v_fu_id;
END $patch$;
