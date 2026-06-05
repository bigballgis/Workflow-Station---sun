#!/usr/bin/env node
/**
 * Export Function Unit fu-20260422-23tfag (id=5) from dev Postgres into 00-init-kk.sql.
 * Usage: node export-from-db.mjs
 */
import { execSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const FU_ID = 5;
const OUT = path.join(path.dirname(fileURLToPath(import.meta.url)), '00-init-kk.sql');

function psqlJson(sql) {
  const cmd = `docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t -A -c "${sql.replace(/"/g, '\\"')}"`;
  const out = execSync(cmd, { encoding: 'utf8', maxBuffer: 50 * 1024 * 1024 }).trim();
  if (!out) return [];
  return out.split('\n').filter(Boolean).map((line) => JSON.parse(line));
}

function sqlVal(v) {
  if (v === null || v === undefined) return 'NULL';
  if (typeof v === 'boolean') return v ? 'true' : 'false';
  if (typeof v === 'number') return String(v);
  if (typeof v === 'object') {
    const s = JSON.stringify(v).replace(/'/g, "''");
    return `'${s}'::jsonb`;
  }
  const s = String(v).replace(/'/g, "''");
  return `'${s}'`;
}

function sqlJsonb(v) {
  if (v === null || v === undefined) return 'NULL';
  const s = typeof v === 'string' ? v : JSON.stringify(v);
  return `$json$${s}$json$::jsonb`;
}

function insertLine(table, cols, row) {
  const values = cols.map((c) => sqlVal(row[c]));
  return `INSERT INTO ${table} (${cols.join(', ')}) VALUES (${values.join(', ')});`;
}

function insertField(row) {
  const cols = [
    'id', 'table_id', 'field_name', 'data_type', 'length', 'precision_value', 'scale',
    'nullable', 'default_value', 'is_primary_key', 'is_unique', 'display_name', 'sort_order',
    'is_foreign_key', 'ref_table_id', 'ref_primary_key_fields', 'pk_generation_json',
    'fk_display_mode', 'relation_cardinality',
  ];
  const values = cols.map((c) => {
    if (c === 'ref_primary_key_fields' || c === 'pk_generation_json') return sqlJsonb(row[c]);
    return sqlVal(row[c]);
  });
  return `INSERT INTO dw_field_definitions (${cols.join(', ')}) VALUES (${values.join(', ')});`;
}

function insertAction(row) {
  const cols = [
    'id', 'function_unit_id', 'action_name', 'action_type', 'config_json',
    'icon', 'button_color', 'display_name', 'is_default', 'created_at', 'updated_at',
  ];
  const values = cols.map((c) => (c === 'config_json' ? sqlJsonb(row[c]) : sqlVal(row[c])));
  return `INSERT INTO dw_action_definitions (${cols.join(', ')}) VALUES (${values.join(', ')});`;
}

function insertForm(row) {
  const cols = [
    'id', 'function_unit_id', 'form_name', 'form_type', 'config_json', 'display_name',
    'bound_table_id', 'lock_version', 'created_at', 'updated_at', 'field_permissions', 'show_live_values',
  ];
  const values = cols.map((c) => {
    if (c === 'config_json' || c === 'field_permissions') return sqlJsonb(row[c]);
    return sqlVal(row[c]);
  });
  return `INSERT INTO dw_form_definitions (${cols.join(', ')}) VALUES (${values.join(', ')});`;
}

function insertBinding(row) {
  const cols = [
    'id', 'form_id', 'table_id', 'relation_table_id', 'binding_type', 'binding_mode',
    'foreign_key_field', 'binding_link_mode', 'sort_order', 'created_at', 'updated_at',
    'sub_list_view_id', 'sub_mode',
  ];
  const values = cols.map((c) => sqlVal(row[c]));
  return `INSERT INTO dw_form_table_bindings (${cols.join(', ')}) VALUES (${values.join(', ')});`;
}

const cleanup = `-- =============================================================================
-- 17-Multi-Instance-Subtask-Demo (Function Unit fu-20260422-23tfag)
-- Developer-workstation catalog only (dw_* + lookup-related rt_*).
-- Does NOT seed admin users, rt_table_access, or dev-specific audit user IDs.
-- Exported from dev DB on ${new Date().toISOString().slice(0, 10)}
-- =============================================================================
BEGIN;

DO $cleanup$
DECLARE v_fu_id bigint;
BEGIN
  SELECT id INTO v_fu_id FROM dw_function_units
  WHERE id = 5 OR code = 'fu-20260422-23tfag' OR name = 'Multi-Instance Subtask Demo'
  ORDER BY id LIMIT 1;

  IF v_fu_id IS NULL THEN
    RETURN;
  END IF;

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
  DELETE FROM rt_table_data_rows
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

`;

const fu = psqlJson(`SELECT row_to_json(t) FROM (SELECT * FROM dw_function_units WHERE id=${FU_ID}) t`)[0];
const tables = psqlJson(`SELECT row_to_json(t) FROM (SELECT * FROM dw_table_definitions WHERE function_unit_id=${FU_ID} ORDER BY id) t`);
const fields = psqlJson(`SELECT row_to_json(t) FROM (SELECT * FROM dw_field_definitions WHERE table_id IN (SELECT id FROM dw_table_definitions WHERE function_unit_id=${FU_ID}) ORDER BY table_id, sort_order, id) t`);
const actions = psqlJson(`SELECT row_to_json(t) FROM (SELECT * FROM dw_action_definitions WHERE function_unit_id=${FU_ID} ORDER BY id) t`);
const forms = psqlJson(`SELECT row_to_json(t) FROM (SELECT * FROM dw_form_definitions WHERE function_unit_id=${FU_ID} ORDER BY id) t`);
const bindings = psqlJson(`SELECT row_to_json(t) FROM (SELECT * FROM dw_form_table_bindings WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id=${FU_ID}) ORDER BY id) t`);
const viewConfigs = psqlJson(`SELECT row_to_json(t) FROM (SELECT c.* FROM dw_sub_table_view_configs c JOIN dw_form_table_bindings b ON c.binding_id=b.id JOIN dw_form_definitions f ON b.form_id=f.id WHERE f.function_unit_id=${FU_ID} ORDER BY c.id) t`);
const viewFields = psqlJson(`SELECT row_to_json(t) FROM (SELECT vf.* FROM dw_sub_table_view_fields vf JOIN dw_sub_table_view_configs c ON vf.view_config_id=c.id JOIN dw_form_table_bindings b ON c.binding_id=b.id JOIN dw_form_definitions f ON b.form_id=f.id WHERE f.function_unit_id=${FU_ID} ORDER BY vf.view_config_id, vf.sort_order, vf.id) t`);
const relations = psqlJson(`SELECT row_to_json(t) FROM (SELECT * FROM dw_table_relations WHERE function_unit_id=${FU_ID} ORDER BY id) t`);
const processes = psqlJson(`SELECT row_to_json(t) FROM (SELECT * FROM dw_process_definitions WHERE function_unit_id=${FU_ID} ORDER BY id) t`);

// rt_* for relation_table_id=1 (test lookup table)
const rtTableIds = [...new Set(bindings.map((b) => b.relation_table_id).filter((id) => id != null && id > 0))];
let rtTables = [];
let rtFields = [];
let rtVersions = [];
let rtDataRows = [];
for (const tid of rtTableIds) {
  rtTables.push(...psqlJson(`SELECT row_to_json(t) FROM (SELECT * FROM rt_table_definitions WHERE id=${tid}) t`));
  rtFields.push(...psqlJson(`SELECT row_to_json(t) FROM (SELECT * FROM rt_field_definitions WHERE table_id=${tid} ORDER BY sort_order, id) t`));
  rtVersions.push(...psqlJson(`SELECT row_to_json(t) FROM (SELECT * FROM rt_table_versions WHERE table_id=${tid} ORDER BY id) t`));
  rtDataRows.push(...psqlJson(`SELECT row_to_json(t) FROM (SELECT * FROM rt_table_data_rows WHERE table_id=${tid}) t`));
}

const lines = [cleanup, ''];

lines.push(insertLine('dw_function_units', [
  'id', 'code', 'name', 'display_name', 'icon_id', 'status', 'current_version', 'version',
  'is_active', 'enabled', 'deployed_at', 'previous_version_id', 'lock_version',
  'created_by', 'created_at', 'updated_by', 'updated_at',
], fu));
lines.push('');

for (const row of tables) {
  lines.push(insertLine('dw_table_definitions', [
    'id', 'function_unit_id', 'table_name', 'table_display_name', 'table_type', 'display_name', 'created_at', 'updated_at',
  ], row));
}
lines.push('');

for (const row of fields) lines.push(insertField(row));
lines.push('');

for (const row of actions) lines.push(insertAction(row));
lines.push('');

for (const row of forms) lines.push(insertForm(row));
lines.push('');

for (const row of bindings) lines.push(insertBinding(row));
lines.push('');

for (const row of viewConfigs) {
  lines.push(insertLine('dw_sub_table_view_configs', ['id', 'binding_id', 'created_at', 'updated_at'], row));
}
lines.push('');

for (const row of viewFields) {
  lines.push(insertLine('dw_sub_table_view_fields', ['id', 'view_config_id', 'field_name', 'sort_order'], row));
}
lines.push('');

if (rtTables.length) {
  lines.push('-- ---------------------------------------------------------------------------');
  lines.push('-- Relation Table (rt_*) configs linked to this function unit');
  lines.push('-- ---------------------------------------------------------------------------');
  for (const row of rtTables) {
    lines.push(`INSERT INTO rt_table_definitions (id, table_name, display_name, description, status, enabled, portal_visible, current_version, created_at, created_by, updated_at, updated_by) VALUES (${row.id},${sqlVal(row.table_name)},${sqlVal(row.display_name)},${sqlVal(row.description)},${sqlVal(row.status)},${sqlVal(row.enabled)},${sqlVal(row.portal_visible)},${sqlVal(row.current_version)},${sqlVal(row.created_at)},${sqlVal(row.created_by)},${sqlVal(row.updated_at)},${sqlVal(row.updated_by)}) ON CONFLICT (table_name) DO UPDATE SET display_name=EXCLUDED.display_name, description=EXCLUDED.description, status=EXCLUDED.status, enabled=EXCLUDED.enabled, portal_visible=EXCLUDED.portal_visible, current_version=EXCLUDED.current_version, updated_at=EXCLUDED.updated_at, updated_by=EXCLUDED.updated_by;`);
  }
  for (const row of rtFields) {
    lines.push(`INSERT INTO rt_field_definitions (id, table_id, field_name, data_type, length, precision_value, scale, nullable, is_primary_key, default_value, display_name, sort_order) VALUES (${row.id},${row.table_id},${sqlVal(row.field_name)},${sqlVal(row.data_type)},${sqlVal(row.length)},${sqlVal(row.precision_value)},${sqlVal(row.scale)},${sqlVal(row.nullable)},${sqlVal(row.is_primary_key)},${sqlVal(row.default_value)},${sqlVal(row.display_name)},${row.sort_order}) ON CONFLICT (id) DO NOTHING;`);
  }
  for (const row of rtVersions) {
    const snap = String(row.snapshot_data).replace(/'/g, "''");
    lines.push(`INSERT INTO rt_table_versions (id, table_id, version_number, snapshot_data, deployed_by, deployed_at, change_log) VALUES (${row.id},${row.table_id},${row.version_number},'${snap}',${sqlVal(row.deployed_by)},${sqlVal(row.deployed_at)},${sqlVal(row.change_log)}) ON CONFLICT (id) DO NOTHING;`);
  }
  for (const row of rtDataRows) {
    const data = JSON.stringify(row.data).replace(/'/g, "''");
    lines.push(`INSERT INTO rt_table_data_rows (table_id, row_id, data, status, created_by, updated_by) VALUES (${row.table_id}, ${sqlVal(row.row_id)}, '${data}'::jsonb, ${sqlVal(row.status)}, ${sqlVal(row.created_by)}, ${sqlVal(row.updated_by)}) ON CONFLICT (table_id, row_id) DO UPDATE SET data = EXCLUDED.data, status = EXCLUDED.status, updated_by = EXCLUDED.updated_by, updated_at = CURRENT_TIMESTAMP;`);
  }
  lines.push('-- (no rt_view_configs linked to kk bindings)');
  lines.push('-- (no rt_view_fields linked to kk view configs)');
  lines.push('-- (no rt_lookup_configs linked to kk forms)');
  lines.push('');
}

for (const row of relations) {
  lines.push(insertLine('dw_table_relations', [
    'id', 'function_unit_id', 'source_table_id', 'source_field_name', 'relation_type',
    'target_table_id', 'target_field_name', 'created_at', 'updated_at',
  ], row));
}
lines.push('');

for (const row of processes) {
  lines.push(`INSERT INTO dw_process_definitions (id, function_unit_id, function_unit_version_id, bpmn_xml, created_at, updated_at) VALUES (${row.id},${row.function_unit_id},${row.function_unit_version_id},${sqlVal(row.bpmn_xml)},${sqlVal(row.created_at)},${sqlVal(row.updated_at)});`);
}
lines.push('');

lines.push('COMMIT;');
lines.push('');

writeFileSync(OUT, lines.join('\n'), 'utf8');
console.log(`Wrote ${OUT} (${lines.length} lines)`);
console.log(`FU version: ${fu.current_version}, tables: ${tables.length}, fields: ${fields.length}, actions: ${actions.length}, forms: ${forms.length}`);
