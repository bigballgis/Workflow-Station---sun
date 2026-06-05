-- =============================================================================
-- V316: Migrate dw_form_definitions.config_json embedded field labels
--       comment / description → displayName (subListViews + relationViews)
--
-- Complements V315 (dw_field_definitions column rename). Existing saved forms
-- may still store subListViews.columns[].comment or relationViews.allFields
-- with comment/description — normalize to displayName with no legacy keys left.
-- =============================================================================

CREATE OR REPLACE FUNCTION ws_migrate_column_display_name(col jsonb) RETURNS jsonb AS $$
DECLARE
  lbl text;
BEGIN
  IF col IS NULL OR col = 'null'::jsonb THEN
    RETURN col;
  END IF;
  lbl := COALESCE(
    NULLIF(col->>'displayName', ''),
    NULLIF(col->>'comment', ''),
    NULLIF(col->>'description', '')
  );
  IF lbl IS NULL THEN
    RETURN col - 'comment' - 'description';
  END IF;
  RETURN (col - 'comment' - 'description') || jsonb_build_object('displayName', lbl);
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION ws_migrate_columns_array(arr jsonb) RETURNS jsonb AS $$
DECLARE
  out jsonb := '[]'::jsonb;
  elem jsonb;
BEGIN
  IF arr IS NULL OR jsonb_typeof(arr) <> 'array' THEN
    RETURN arr;
  END IF;
  FOR elem IN SELECT jsonb_array_elements(arr) LOOP
    out := out || jsonb_build_array(ws_migrate_column_display_name(elem));
  END LOOP;
  RETURN out;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION ws_migrate_sub_list_views(slv jsonb) RETURNS jsonb AS $$
DECLARE
  out jsonb := '{}'::jsonb;
  k text;
  v jsonb;
BEGIN
  IF slv IS NULL OR jsonb_typeof(slv) <> 'object' THEN
    RETURN slv;
  END IF;
  FOR k, v IN SELECT * FROM jsonb_each(slv) LOOP
    IF jsonb_typeof(v) = 'object' AND v ? 'columns' THEN
      out := out || jsonb_build_object(k, jsonb_set(v, '{columns}', ws_migrate_columns_array(v->'columns')));
    ELSE
      out := out || jsonb_build_object(k, v);
    END IF;
  END LOOP;
  RETURN out;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION ws_migrate_relation_view_entry(entry jsonb) RETURNS jsonb AS $$
BEGIN
  IF entry IS NULL OR jsonb_typeof(entry) <> 'object' THEN
    RETURN entry;
  END IF;
  IF entry ? 'allFields' THEN
    entry := jsonb_set(entry, '{allFields}', ws_migrate_columns_array(entry->'allFields'));
  END IF;
  IF entry ? 'viewFields' THEN
    entry := jsonb_set(entry, '{viewFields}', ws_migrate_columns_array(entry->'viewFields'));
  END IF;
  RETURN entry;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION ws_migrate_relation_views(rv jsonb) RETURNS jsonb AS $$
DECLARE
  out jsonb := '{}'::jsonb;
  k text;
  v jsonb;
BEGIN
  IF rv IS NULL OR jsonb_typeof(rv) <> 'object' THEN
    RETURN rv;
  END IF;
  FOR k, v IN SELECT * FROM jsonb_each(rv) LOOP
    out := out || jsonb_build_object(k, ws_migrate_relation_view_entry(v));
  END LOOP;
  RETURN out;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

CREATE OR REPLACE FUNCTION ws_migrate_form_config_display_names(cfg jsonb) RETURNS jsonb AS $$
BEGIN
  IF cfg IS NULL THEN
    RETURN cfg;
  END IF;
  IF cfg ? 'subListViews' THEN
    cfg := jsonb_set(cfg, '{subListViews}', ws_migrate_sub_list_views(cfg->'subListViews'));
  END IF;
  IF cfg ? 'relationViews' THEN
    cfg := jsonb_set(cfg, '{relationViews}', ws_migrate_relation_views(cfg->'relationViews'));
  END IF;
  RETURN cfg;
END;
$$ LANGUAGE plpgsql IMMUTABLE;

UPDATE dw_form_definitions
SET config_json = ws_migrate_form_config_display_names(config_json)
WHERE config_json IS NOT NULL
  AND (config_json ? 'subListViews' OR config_json ? 'relationViews');

DROP FUNCTION IF EXISTS ws_migrate_form_config_display_names(jsonb);
DROP FUNCTION IF EXISTS ws_migrate_relation_views(jsonb);
DROP FUNCTION IF EXISTS ws_migrate_relation_view_entry(jsonb);
DROP FUNCTION IF EXISTS ws_migrate_sub_list_views(jsonb);
DROP FUNCTION IF EXISTS ws_migrate_columns_array(jsonb);
DROP FUNCTION IF EXISTS ws_migrate_column_display_name(jsonb);
