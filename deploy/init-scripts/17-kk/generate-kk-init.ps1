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

# ---------------------------------------------------------------------------
# Admin-center lookup tables: export schema + data for tables referenced by kk
# (e.g. lookupConfig.tableName like sys_users / tes2)
# ---------------------------------------------------------------------------
$formJsonAllLines = Exec-Psql "select config_json::text from dw_form_definitions where function_unit_id=$fuId order by id;"
$formJsonAll = ($formJsonAllLines -join "`n")
$adminTables = New-Object System.Collections.Generic.HashSet[string]

foreach ($m in [regex]::Matches($formJsonAll, '\"tableName\":\"([^\"]+)\"')) {
  $null = $adminTables.Add($m.Groups[1].Value)
}

# lookupConfig is stored as an escaped JSON string inside config_json, so also match \"tableName\":\"...\"
foreach ($m in [regex]::Matches($formJsonAll, 'tableName\\\\\":\\\\\"([^\\\\\"]+)\\\\\"')) {
  $null = $adminTables.Add($m.Groups[1].Value)
}

if ($adminTables.Count -gt 0) {
  Write-Host ("Detected admin-center tables in lookups: " + (($adminTables | Sort-Object) -join ", "))
}
else {
  # Fallback: kk commonly references these admin-center tables via lookupConfig
  foreach ($t in @("sys_users","tes2")) { $null = $adminTables.Add($t) }
}

$adminDumpSqlParts = @()
foreach ($tbl in ($adminTables | Sort-Object)) {
  # Only dump if table exists in public schema
  $tblSql = $tbl.Replace("'", "''")
  $exists = Exec-Psql "select count(*) from information_schema.tables where table_schema='public' and table_name='$tblSql';"
  if ($exists -ne "1") { continue }

  Write-Host "Dumping admin table data (seed-if-empty): public.$tbl"

  # Data only; schema is owned by admin-center baseline init scripts.
  $dataDump = & pg_dump --data-only --column-inserts --no-owner --no-privileges -t "public.$tbl" $Conn
  if ($LASTEXITCODE -ne 0) { throw "pg_dump data-only failed for $tbl" }

  # Keep only plain INSERT statements (pg_dump may include psql meta like \unrestrict)
  $dataDump = ($dataDump -split "`r?`n" | Where-Object { $_ -match '^INSERT INTO ' }) -join "`n"

  # Make sys_users inserts idempotent on PK
  if ($tbl -eq "sys_users") {
    $dataDump = [regex]::Replace(
      $dataDump,
      '(?m)^INSERT INTO public\\.sys_users\\b([^;]*);$',
      'INSERT INTO public.sys_users$1 ON CONFLICT (id) DO NOTHING;'
    )
  }

  $blockTag = "seed_admin_" + $tbl
  $adminDumpSqlParts += ""
  $adminDumpSqlParts += "-- ============================================================================"
  $adminDumpSqlParts += "-- Admin-center table seed: public.$tbl"
  $adminDumpSqlParts += "--   - only runs when table exists AND is empty"
  $adminDumpSqlParts += "-- ============================================================================"
  $adminDumpSqlParts += "DO $" + $blockTag + "$"
  $adminDumpSqlParts += "BEGIN"
  $adminDumpSqlParts += "  IF to_regclass('public.$tbl') IS NULL THEN"
  $adminDumpSqlParts += "    RAISE NOTICE 'admin table public.$tbl not found, skip seed.';"
  $adminDumpSqlParts += "  ELSIF (SELECT count(*) FROM public.$tbl) = 0 THEN"
  $adminDumpSqlParts += $dataDump
  $adminDumpSqlParts += "  ELSE"
  $adminDumpSqlParts += "    RAISE NOTICE 'admin table public.$tbl already has data, skip seed.';"
  $adminDumpSqlParts += "  END IF;"
  $adminDumpSqlParts += "END $" + $blockTag + "$;"
}

$adminDumpSql = ($adminDumpSqlParts -join "`n")

$fuInsert = Exec-Psql @"
select
  'INSERT INTO dw_function_units (id, code, name, description, icon_id, status, current_version, version, is_active, enabled, deployed_at, previous_version_id, lock_version, created_by, created_at, updated_by, updated_at) VALUES ('||
  id||','||quote_literal(code)||','||quote_literal(name)||','||coalesce(quote_literal(description),'NULL')||','||coalesce(quote_literal(icon_id),'NULL')||','||
  quote_literal(status)||','||quote_literal(current_version)||','||quote_literal(version)||','||
  (case when is_active then 'true' else 'false' end)||','||(case when enabled then 'true' else 'false' end)||','||
  coalesce(quote_literal(deployed_at::text),'NULL')||','||coalesce(previous_version_id::text,'NULL')||','||lock_version||','||
  coalesce(quote_literal(created_by),'NULL')||','||quote_literal(created_at::text)||','||coalesce(quote_literal(updated_by),'NULL')||','||quote_literal(updated_at::text)||
  ');'
from dw_function_units where id = $fuId;
"@

$tableInserts = Exec-Psql "select 'INSERT INTO dw_table_definitions (id, function_unit_id, table_name, table_display_name, table_type, description, created_at, updated_at) VALUES ('|| id ||','|| function_unit_id ||','|| quote_literal(table_name) ||','|| coalesce(quote_literal(table_display_name),'NULL') ||','|| quote_literal(table_type) ||','|| coalesce(quote_literal(description),'NULL') ||','|| quote_literal(created_at::text) ||','|| quote_literal(updated_at::text) ||');' from dw_table_definitions where function_unit_id=$fuId order by id;"

$fieldInserts = Exec-Psql "select 'INSERT INTO dw_field_definitions (id, table_id, field_name, data_type, length, precision_value, scale, nullable, default_value, is_primary_key, is_unique, description, sort_order) VALUES ('|| id ||','|| table_id ||','|| quote_literal(field_name) ||','|| quote_literal(data_type) ||','|| coalesce(length::text,'NULL') ||','|| coalesce(precision_value::text,'NULL') ||','|| coalesce(scale::text,'NULL') ||','|| (case when nullable then 'true' else 'false' end) ||','|| coalesce(quote_literal(default_value),'NULL') ||','|| (case when is_primary_key then 'true' else 'false' end) ||','|| (case when is_unique then 'true' else 'false' end) ||','|| coalesce(quote_literal(description),'NULL') ||','|| coalesce(sort_order::text,'NULL') ||');' from dw_field_definitions where table_id in (select id from dw_table_definitions where function_unit_id=$fuId) order by table_id, sort_order, id;"

$actionInserts = Exec-Psql "select 'INSERT INTO dw_action_definitions (id, function_unit_id, action_name, action_type, config_json, icon, button_color, description, is_default, created_at, updated_at) VALUES ('|| id ||','|| function_unit_id ||','|| quote_literal(action_name) ||','|| quote_literal(action_type) ||','|| quote_literal(config_json::text) ||'::jsonb,'|| coalesce(quote_literal(icon),'NULL') ||','|| coalesce(quote_literal(button_color),'NULL') ||','|| coalesce(quote_literal(description),'NULL') ||','|| (case when is_default then 'true' else 'false' end) ||','|| quote_literal(created_at::text) ||','|| quote_literal(updated_at::text) ||');' from dw_action_definitions where function_unit_id=$fuId order by id;"

$formInserts = Exec-Psql "select 'INSERT INTO dw_form_definitions (id, function_unit_id, form_name, form_type, config_json, description, bound_table_id, lock_version, created_at, updated_at, field_permissions, show_live_values) VALUES ('|| id ||','|| function_unit_id ||','|| quote_literal(form_name) ||','|| quote_literal(form_type) ||','|| quote_literal(config_json::text) ||'::jsonb,'|| coalesce(quote_literal(description),'NULL') ||','|| coalesce(bound_table_id::text,'NULL') ||','|| lock_version ||','|| quote_literal(created_at::text) ||','|| quote_literal(updated_at::text) ||','|| coalesce(quote_literal(field_permissions::text),'NULL') ||','|| coalesce((case when show_live_values is null then 'NULL' when show_live_values then 'true' else 'false' end),'NULL') ||');' from dw_form_definitions where function_unit_id=$fuId order by id;"

$bindingInserts = Exec-Psql "select 'INSERT INTO dw_form_table_bindings (id, form_id, table_id, relation_table_id, binding_type, binding_mode, foreign_key_field, sort_order, created_at, updated_at, sub_list_view_id, standalone_form_id, sub_mode) VALUES ('|| id ||','|| form_id ||','|| table_id ||','|| coalesce(relation_table_id::text,'NULL') ||','|| quote_literal(binding_type) ||','|| quote_literal(binding_mode) ||','|| coalesce(quote_literal(foreign_key_field),'NULL') ||','|| coalesce(sort_order::text,'NULL') ||','|| quote_literal(created_at::text) ||','|| quote_literal(updated_at::text) ||','|| coalesce(sub_list_view_id::text,'NULL') ||','|| coalesce(standalone_form_id::text,'NULL') ||','|| coalesce(quote_literal(sub_mode),'NULL') ||');' from dw_form_table_bindings where form_id in (select id from dw_form_definitions where function_unit_id=$fuId) order by form_id, sort_order, id;"

$stageBindingInserts = Exec-Psql "select 'INSERT INTO dw_form_stage_bindings (id, form_id, stage_id, stage_name, created_at) VALUES ('|| id ||','|| form_id ||','|| stage_id ||','|| quote_literal(stage_name) ||','|| quote_literal(created_at::text) ||');' from dw_form_stage_bindings where form_id in (select id from dw_form_definitions where function_unit_id=$fuId) order by form_id, id;"

$tableRelationInserts = Exec-Psql "select 'INSERT INTO dw_table_relations (id, function_unit_id, source_table_id, source_field_name, relation_type, target_table_id, target_field_name, created_at, updated_at) VALUES ('|| id ||','|| function_unit_id ||','|| source_table_id ||','|| quote_literal(source_field_name) ||','|| quote_literal(relation_type) ||','|| target_table_id ||','|| quote_literal(target_field_name) ||','|| quote_literal(created_at::text) ||','|| quote_literal(updated_at::text) ||');' from dw_table_relations where function_unit_id=$fuId order by id;"

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

  DELETE FROM dw_form_table_bindings WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id);
  DELETE FROM dw_form_stage_bindings WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id = v_fu_id);
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

$patchPath = "deploy/init-scripts/99-maintenance/01-add-save-action-to-kk.sql"
if (-not (Test-Path $patchPath)) { throw "Patch file not found: $patchPath" }
$patchSql = Get-Content -Path $patchPath -Raw -Encoding UTF8

$footer = @"
COMMIT;
"@

$sqlOut = @()
$sqlOut += $header
$sqlOut += ""
$sqlOut += "-- ---------------------------------------------------------------------------"
$sqlOut += "-- Admin-center tables referenced by kk (lookup tables)"
$sqlOut += "-- ---------------------------------------------------------------------------"
if ($adminDumpSql.Trim().Length -gt 0) {
  $sqlOut += $adminDumpSql
} else {
  $sqlOut += "-- (none detected or none exist in DB)"
}
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
$sqlOut += $stageBindingInserts
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

New-Item -Force -ItemType File -Path $Output | Out-Null
Set-Content -Path $Output -Value ($sqlOut -join "`n") -Encoding UTF8

Write-Host "Generated: $Output"
