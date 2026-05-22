Param(
  [string]$Conn = "postgresql://platform_dev:dev_password_123@localhost:5432/workflow_platform_dev",
  [string]$Output = "deploy/init-scripts/17-kk/00-init-kk.sql",
  [string]$FuCode = "fu-20260422-23tfag",
  [string]$FuName = "kk"
)

$ErrorActionPreference = "Stop"

# Ensure Unicode (avoid mojibake like '保存' -> garbled) and stable psql output
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$env:PGCLIENTENCODING = "UTF8"

function Exec-Psql([string]$sql) {
  & psql -v ON_ERROR_STOP=1 -v QUIET=1 -At -c $sql $Conn
  if ($LASTEXITCODE -ne 0) { throw "psql failed: $sql" }
}

Write-Host "Exporting Function Unit snapshot for code='$FuCode' name='$FuName'..."

$fuId = Exec-Psql "select id from dw_function_units where code = '$FuCode' or name = '$FuName' order by id limit 1;"
if (-not $fuId) { throw "Function unit not found by code/name." }

$bpmnBase64 = Exec-Psql "select regexp_replace(bpmn_xml, E'\\s+', '', 'g') from dw_process_definitions where function_unit_id = $fuId order by updated_at desc limit 1;"

$fuInsert = Exec-Psql "select 'INSERT INTO dw_function_units (id, code, name, description, icon_id, status, current_version, version, is_active, enabled, deployed_at, previous_version_id, lock_version, created_by, created_at, updated_by, updated_at) VALUES ('|| id||','||quote_literal(code)||','||quote_literal(name)||','||coalesce(quote_literal(description),'NULL')||','||coalesce(quote_literal(icon_id),'NULL')||','|| quote_literal(status)||','||quote_literal(current_version)||','||quote_literal(version)||','|| (case when is_active then 'true' else 'false' end)||','||(case when enabled then 'true' else 'false' end)||','|| coalesce(quote_literal(deployed_at::text),'NULL')||','||coalesce(previous_version_id::text,'NULL')||','||lock_version||','|| quote_literal('system')||','||quote_literal(created_at::text)||','||quote_literal('system')||','||quote_literal(updated_at::text)|| ');' from dw_function_units where id = $fuId;"

$tableInserts = Exec-Psql "select 'INSERT INTO dw_table_definitions (id, function_unit_id, table_name, table_display_name, table_type, description, created_at, updated_at) VALUES ('|| id ||','|| function_unit_id ||','|| quote_literal(table_name) ||','|| coalesce(quote_literal(table_display_name),'NULL') ||','|| quote_literal(table_type) ||','|| coalesce(quote_literal(description),'NULL') ||','|| quote_literal(created_at::text) ||','|| quote_literal(updated_at::text) ||');' from dw_table_definitions where function_unit_id=$fuId order by id;"

$fieldInserts = Exec-Psql "select 'INSERT INTO dw_field_definitions (id, table_id, field_name, data_type, length, precision_value, scale, nullable, default_value, is_primary_key, is_unique, description, sort_order) VALUES ('|| id ||','|| table_id ||','|| quote_literal(field_name) ||','|| quote_literal(data_type) ||','|| coalesce(length::text,'NULL') ||','|| coalesce(precision_value::text,'NULL') ||','|| coalesce(scale::text,'NULL') ||','|| (case when nullable then 'true' else 'false' end) ||','|| coalesce(quote_literal(default_value),'NULL') ||','|| (case when is_primary_key then 'true' else 'false' end) ||','|| (case when is_unique then 'true' else 'false' end) ||','|| coalesce(quote_literal(description),'NULL') ||','|| coalesce(sort_order::text,'NULL') ||');' from dw_field_definitions where table_id in (select id from dw_table_definitions where function_unit_id=$fuId) order by table_id, sort_order, id;"

$actionInserts = Exec-Psql "select 'INSERT INTO dw_action_definitions (id, function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default, created_at, updated_at) VALUES ('|| id ||','|| function_unit_id ||','|| quote_literal(action_name) ||','|| quote_literal(action_type) ||','|| quote_literal(config_json::text) ||'::jsonb,'|| coalesce(quote_literal(icon),'NULL') ||','|| coalesce(quote_literal(button_color),'NULL') ||','|| coalesce(quote_literal(description),'NULL') ||','|| (case when is_default then 'true' else 'false' end) ||','|| quote_literal(created_at::text) ||','|| quote_literal(updated_at::text) ||');' from dw_action_definitions where function_unit_id=$fuId order by id;"

$formInserts = Exec-Psql "select 'INSERT INTO dw_form_definitions (id, function_unit_id, form_name, form_type, config_json, description, bound_table_id, lock_version, created_at, updated_at, field_permissions, show_live_values) VALUES ('|| id ||','|| function_unit_id ||','|| quote_literal(form_name) ||','|| quote_literal(form_type) ||','|| quote_literal(config_json::text) ||'::jsonb,'|| coalesce(quote_literal(description),'NULL') ||','|| coalesce(bound_table_id::text,'NULL') ||','|| lock_version ||','|| quote_literal(created_at::text) ||','|| quote_literal(updated_at::text) ||','|| coalesce(quote_literal(field_permissions::text),'NULL') ||','|| coalesce((case when show_live_values is null then 'NULL' when show_live_values then 'true' else 'false' end),'NULL') ||');' from dw_form_definitions where function_unit_id=$fuId order by id;"

$bindingInserts = Exec-Psql "select 'INSERT INTO dw_form_table_bindings (id, form_id, table_id, relation_table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at, sub_list_view_id, sub_mode) VALUES ('|| id ||','|| form_id ||','|| coalesce(table_id::text,'NULL') ||','|| coalesce(relation_table_id::text,'NULL') ||','|| quote_literal(binding_type) ||','|| quote_literal(binding_mode) ||','|| coalesce(quote_literal(foreign_key_field),'NULL') ||','|| coalesce(sort_order::text,'NULL') ||','|| quote_literal(created_at::text) ||','|| quote_literal(updated_at::text) ||','|| coalesce(sub_list_view_id::text,'NULL') ||','|| coalesce(quote_literal(sub_mode),'NULL') ||');' from dw_form_table_bindings where form_id in (select id from dw_form_definitions where function_unit_id=$fuId) order by form_id, sort_order, id;"

$tableRelationInserts = Exec-Psql "select 'INSERT INTO dw_table_relations (id, function_unit_id, source_table_id, source_field_name, relation_type, target_table_id, target_field_name, created_at, updated_at) VALUES ('|| id ||','|| function_unit_id ||','|| source_table_id ||','|| quote_literal(source_field_name) ||','|| quote_literal(relation_type) ||','|| target_table_id ||','|| quote_literal(target_field_name) ||','|| quote_literal(created_at::text) ||','|| quote_literal(updated_at::text) ||');' from dw_table_relations where function_unit_id=$fuId order by id;"

# ---------------------------------------------------------------------------
# Relation Table (rt_*) view/lookup configs
# - Relation tables themselves are global (rt_table_definitions); per-form configs live in rt_view_configs/fields and rt_lookup_configs.
# - We export configs that are linked to kk bindings/forms so Form Designer can render relation-table views and lookup views.
# ---------------------------------------------------------------------------

# Relation Table definitions referenced by kk (RELATED bindings with relation_table_id > 0)
$rtTableDefInserts = Exec-Psql "select 'INSERT INTO rt_table_definitions (id, table_name, display_name, description, status, enabled, portal_visible, current_version, created_at, created_by, updated_at, updated_by) VALUES ('|| id||','||quote_literal(table_name)||','||coalesce(quote_literal(display_name),'NULL')||','||coalesce(quote_literal(description),'NULL')||','|| quote_literal(status)||','||(case when enabled then 'true' else 'false' end)||','||(case when portal_visible then 'true' else 'false' end)||','|| coalesce(current_version::text,'NULL')||','||quote_literal(created_at::text)||','||quote_literal('system')||','|| quote_literal(updated_at::text)||','||quote_literal('system')|| ') ON CONFLICT (table_name) DO UPDATE SET display_name=EXCLUDED.display_name, description=EXCLUDED.description, status=EXCLUDED.status, enabled=EXCLUDED.enabled, portal_visible=EXCLUDED.portal_visible, current_version=EXCLUDED.current_version, updated_at=EXCLUDED.updated_at, updated_by=EXCLUDED.updated_by;' from rt_table_definitions where id in (select distinct relation_table_id from dw_form_table_bindings where form_id in (select id from dw_form_definitions where function_unit_id = $fuId) and relation_table_id is not null and relation_table_id > 0) order by id;"

$rtFieldDefInserts = Exec-Psql "select 'INSERT INTO rt_field_definitions (id, table_id, field_name, data_type, length, precision_value, scale, nullable, is_primary_key, default_value, comment, sort_order) VALUES ('|| id||','||table_id||','||quote_literal(field_name)||','||quote_literal(data_type)||','|| coalesce(length::text,'NULL')||','||coalesce(precision_value::text,'NULL')||','||coalesce(scale::text,'NULL')||','|| (case when nullable then 'true' else 'false' end)||','|| (case when is_primary_key then 'true' else 'false' end)||','|| coalesce(quote_literal(default_value),'NULL')||','||coalesce(quote_literal(comment),'NULL')||','||sort_order|| ') ON CONFLICT (id) DO NOTHING;' from rt_field_definitions where table_id in (select id from rt_table_definitions where id in (select distinct relation_table_id from dw_form_table_bindings where form_id in (select id from dw_form_definitions where function_unit_id = $fuId) and relation_table_id is not null and relation_table_id > 0)) order by table_id, sort_order, id;"

$rtTableVersionInserts = Exec-Psql "select 'INSERT INTO rt_table_versions (id, table_id, version_number, snapshot_data, deployed_by, deployed_at, change_log) VALUES ('|| id||','||table_id||','||version_number||','||quote_literal(snapshot_data)||','||quote_literal('system')||','|| quote_literal(deployed_at::text)||','||coalesce(quote_literal(change_log),'NULL')|| ') ON CONFLICT (id) DO NOTHING;' from rt_table_versions where table_id in (select distinct relation_table_id from dw_form_table_bindings where form_id in (select id from dw_form_definitions where function_unit_id = $fuId) and relation_table_id is not null and relation_table_id > 0) order by table_id, version_number, id;"

$rtViewConfigInserts = Exec-Psql "select 'INSERT INTO rt_view_configs (id, binding_id, table_id, field_config, created_at, updated_at) VALUES ('|| id||','||binding_id||','||table_id||','||coalesce(quote_literal(field_config),'NULL')||','|| quote_literal(created_at::text)||','||coalesce(quote_literal(updated_at::text),'NULL')|| ');' from rt_view_configs where binding_id in (select id from dw_form_table_bindings where form_id in (select id from dw_form_definitions where function_unit_id = $fuId)) order by id;"

$rtViewFieldInserts = Exec-Psql "select 'INSERT INTO rt_view_fields (id, view_config_id, field_name, display_label, column_width, sort_order, visible) VALUES ('|| id||','||view_config_id||','||quote_literal(field_name)||','||coalesce(quote_literal(display_label),'NULL')||','|| coalesce(column_width::text,'NULL')||','||sort_order||','||(case when visible then 'true' else 'false' end)|| ');' from rt_view_fields where view_config_id in (select id from rt_view_configs where binding_id in (select id from dw_form_table_bindings where form_id in (select id from dw_form_definitions where function_unit_id = $fuId))) order by view_config_id, sort_order, id;"

$rtLookupConfigInserts = Exec-Psql "select 'INSERT INTO rt_lookup_configs (id, form_id, component_id, view_config_id, table_id, search_fields, display_field, created_at, updated_at) VALUES ('|| id||','||form_id||','||quote_literal(component_id)||','||coalesce(view_config_id::text,'NULL')||','||table_id||','|| coalesce(quote_literal(search_fields),'NULL')||','||coalesce(quote_literal(display_field),'NULL')||','|| quote_literal(created_at::text)||','||coalesce(quote_literal(updated_at::text),'NULL')|| ');' from rt_lookup_configs where form_id in (select id from dw_form_definitions where function_unit_id = $fuId) order by form_id, id;"

$procRow = Exec-Psql "select id, function_unit_version_id, created_at::text, updated_at::text from dw_process_definitions where function_unit_id=$fuId order by updated_at desc limit 1;"
$procParts = $procRow.Split('|')
$procId = $procParts[0]
$procFuVerId = $procParts[1]
$procCreatedAt = $procParts[2]
$procUpdatedAt = $procParts[3]

$headerTemplate = @'
-- Auto-generated by generate-kk-init.ps1
BEGIN;

DO $cleanup$
DECLARE v_fu_id bigint;
BEGIN
  SELECT id INTO v_fu_id FROM dw_function_units
  WHERE id = __FU_ID__ OR code = '__FU_CODE__' OR name = '__FU_NAME__'
  ORDER BY id LIMIT 1;

  IF v_fu_id IS NULL THEN
    RETURN;
  END IF;

  -- Clean relation-table configs linked to this function unit (rt_* tables are global, but configs are tied to bindings/forms)
  DELETE FROM rt_view_fields
    WHERE view_config_id IN (
      SELECT id FROM rt_view_configs
      WHERE binding_id IN (
        SELECT id FROM dw_form_table_bindings
        WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id)
      )
    );
  DELETE FROM rt_view_configs
    WHERE binding_id IN (
      SELECT id FROM dw_form_table_bindings
      WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id)
    );
  DELETE FROM rt_lookup_configs
    WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id);

  DELETE FROM rt_view_fields
    WHERE view_config_id IN (
      SELECT id FROM rt_view_configs
      WHERE table_id IN (
        SELECT DISTINCT relation_table_id
        FROM dw_form_table_bindings
        WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id)
          AND relation_table_id IS NOT NULL
          AND relation_table_id > 0
      )
    );
  DELETE FROM rt_view_configs
    WHERE table_id IN (
      SELECT DISTINCT relation_table_id
      FROM dw_form_table_bindings
      WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id)
        AND relation_table_id IS NOT NULL
        AND relation_table_id > 0
    );
  DELETE FROM rt_lookup_configs
    WHERE table_id IN (
      SELECT DISTINCT relation_table_id
      FROM dw_form_table_bindings
      WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id)
        AND relation_table_id IS NOT NULL
        AND relation_table_id > 0
    );
  DELETE FROM rt_field_definitions
    WHERE table_id IN (
      SELECT DISTINCT relation_table_id
      FROM dw_form_table_bindings
      WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id)
        AND relation_table_id IS NOT NULL
        AND relation_table_id > 0
    );
  DELETE FROM rt_table_versions
    WHERE table_id IN (
      SELECT DISTINCT relation_table_id
      FROM dw_form_table_bindings
      WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id)
        AND relation_table_id IS NOT NULL
        AND relation_table_id > 0
    );
  DELETE FROM rt_table_definitions
    WHERE id IN (
      SELECT DISTINCT relation_table_id
      FROM dw_form_table_bindings
      WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id)
        AND relation_table_id IS NOT NULL
        AND relation_table_id > 0
    );

  DELETE FROM dw_form_table_bindings WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id);
  DELETE FROM dw_form_definitions WHERE function_unit_id = v_fu_id;
  DELETE FROM dw_process_definitions WHERE function_unit_id = v_fu_id;
  DELETE FROM dw_action_definitions WHERE function_unit_id = v_fu_id;
  DELETE FROM dw_table_relations WHERE function_unit_id = v_fu_id;
  DELETE FROM dw_field_definitions WHERE table_id IN (SELECT id FROM dw_table_definitions WHERE function_unit_id = v_fu_id);
  DELETE FROM dw_table_definitions WHERE function_unit_id = v_fu_id;
  DELETE FROM dw_function_units WHERE id = v_fu_id;
END $cleanup$;

'@

$header = $headerTemplate.
  Replace('__FU_ID__', $fuId).
  Replace('__FU_CODE__', $FuCode.Replace("'","''")).
  Replace('__FU_NAME__', $FuName.Replace("'","''"))

$processInsert = @"
INSERT INTO dw_process_definitions (id, function_unit_id, function_unit_version_id, bpmn_xml, created_at, updated_at)
VALUES ($procId, $fuId, $procFuVerId, $(("'" + $bpmnBase64.Replace("'","''") + "'")), '$procCreatedAt', '$procUpdatedAt');

"@

$patchPath = "deploy/init-scripts/17-kk/01-add-save-action.sql"
if (-not (Test-Path $patchPath)) { throw "Patch file not found: $patchPath" }
$patchSql = Get-Content -Path $patchPath -Raw -Encoding UTF8

$footer = @"
COMMIT;
"@

$sqlOut = @()
$sqlOut += $header
$sqlOut += ""
$sqlOut += $fuInsert
$sqlOut += ""
$sqlOut += $tableInserts
$sqlOut += ""
$sqlOut += $fieldInserts
$sqlOut += ""
$sqlOut += $actionInserts
$sqlOut += ""
$sqlOut += $formInserts
$sqlOut += ""
$sqlOut += $bindingInserts
$sqlOut += ""
$sqlOut += "-- ---------------------------------------------------------------------------"
$sqlOut += "-- Relation Table (rt_*) configs linked to kk (optional; may be empty)"
$sqlOut += "-- ---------------------------------------------------------------------------"
$sqlOut += "-- rt_table_definitions / rt_field_definitions / rt_table_versions"
if (($rtTableDefInserts -join "").Trim().Length -gt 0) {
  $sqlOut += $rtTableDefInserts
} else {
  $sqlOut += "-- (no rt_table_definitions referenced by kk)"
}
$sqlOut += ""
if (($rtFieldDefInserts -join "").Trim().Length -gt 0) {
  $sqlOut += $rtFieldDefInserts
} else {
  $sqlOut += "-- (no rt_field_definitions referenced by kk)"
}
$sqlOut += ""
if (($rtTableVersionInserts -join "").Trim().Length -gt 0) {
  $sqlOut += $rtTableVersionInserts
} else {
  $sqlOut += "-- (no rt_table_versions referenced by kk)"
}
$sqlOut += ""
if (($rtViewConfigInserts -join "").Trim().Length -gt 0) {
  $sqlOut += $rtViewConfigInserts
} else {
  $sqlOut += "-- (no rt_view_configs linked to kk bindings)"
}
$sqlOut += ""
if (($rtViewFieldInserts -join "").Trim().Length -gt 0) {
  $sqlOut += $rtViewFieldInserts
} else {
  $sqlOut += "-- (no rt_view_fields linked to kk view configs)"
}
$sqlOut += ""
if (($rtLookupConfigInserts -join "").Trim().Length -gt 0) {
  $sqlOut += $rtLookupConfigInserts
} else {
  $sqlOut += "-- (no rt_lookup_configs linked to kk forms)"
}
$sqlOut += ""
$sqlOut += $tableRelationInserts
$sqlOut += ""
$sqlOut += $processInsert
$sqlOut += ""
$sqlOut += "-- ---------------------------------------------------------------------------"
$sqlOut += "-- 9) Ensure SAVE action exists and is bound in BPMN (Base64-safe)"
$sqlOut += "--     (inlined from $patchPath)"
$sqlOut += "-- ---------------------------------------------------------------------------"
$sqlOut += $patchSql
$sqlOut += $footer

$sqlText = ($sqlOut -join "`n")
# Developer-workstation init: no admin user rows, no per-user ACL, no dev audit user ids.
$sqlText = $sqlText -replace '(?m)^.*public\.sys_users.*\r?\n?', ''
$sqlText = $sqlText -replace 'public\.', ''
$sqlText = [regex]::Replace($sqlText, '(?m)^INSERT INTO dw_form_stage_bindings\b.*;\r?\n?', '')
$sqlText = [regex]::Replace($sqlText, '(?m)^INSERT INTO rt_table_access\b.*;\r?\n?', '')
$sqlText = [regex]::Replace($sqlText, '(?m)^.*sys_action_definitions.*\r?\n?', '')
# Remove dev-user ids accidentally embedded in form lookup filters.
$sqlText = $sqlText -replace '44053631', ''

New-Item -Force -ItemType File -Path $Output | Out-Null
Set-Content -Path $Output -Value $sqlText -Encoding UTF8

Write-Host "Generated: $Output"
