#!/usr/bin/env node
import { execSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';
import crypto from 'node:crypto';

const FU_ID = 5;
const SQL = readFileSync(path.join(path.dirname(fileURLToPath(import.meta.url)), '00-init-kk.sql'), 'utf8');

function psql(q) {
  const cmd = `docker exec platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -t -A -c "${q.replace(/"/g, '\\"')}"`;
  return execSync(cmd, { encoding: 'utf8', maxBuffer: 50 * 1024 * 1024 }).trim().split('\n').filter(Boolean);
}

function insertIds(table) {
  const re = new RegExp(`INSERT INTO ${table}[^;]+VALUES \\((\\d+)`, 'g');
  const ids = [];
  let m;
  while ((m = re.exec(SQL)) !== null) ids.push(Number(m[1]));
  return [...new Set(ids)].sort((a, b) => a - b);
}

function countInsert(table) {
  return (SQL.match(new RegExp(`INSERT INTO ${table}`, 'g')) || []).length;
}

let ok = true;
const log = (status, msg) => {
  if (status === 'FAIL') ok = false;
  console.log(`${status} ${msg}`);
};

console.log('=== Row / ID parity ===');
const tables = [
  ['dw_table_definitions', `SELECT id FROM dw_table_definitions WHERE function_unit_id=${FU_ID} ORDER BY id`],
  ['dw_field_definitions', `SELECT id FROM dw_field_definitions WHERE table_id IN (SELECT id FROM dw_table_definitions WHERE function_unit_id=${FU_ID}) ORDER BY id`],
  ['dw_action_definitions', `SELECT id FROM dw_action_definitions WHERE function_unit_id=${FU_ID} ORDER BY id`],
  ['dw_form_definitions', `SELECT id FROM dw_form_definitions WHERE function_unit_id=${FU_ID} ORDER BY id`],
  ['dw_form_table_bindings', `SELECT id FROM dw_form_table_bindings WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id=${FU_ID}) ORDER BY id`],
  ['dw_sub_table_view_configs', `SELECT c.id FROM dw_sub_table_view_configs c JOIN dw_form_table_bindings b ON c.binding_id=b.id JOIN dw_form_definitions f ON b.form_id=f.id WHERE f.function_unit_id=${FU_ID} ORDER BY c.id`],
  ['dw_sub_table_view_fields', `SELECT vf.id FROM dw_sub_table_view_fields vf JOIN dw_sub_table_view_configs c ON vf.view_config_id=c.id JOIN dw_form_table_bindings b ON c.binding_id=b.id JOIN dw_form_definitions f ON b.form_id=f.id WHERE f.function_unit_id=${FU_ID} ORDER BY vf.id`],
  ['dw_table_relations', `SELECT id FROM dw_table_relations WHERE function_unit_id=${FU_ID} ORDER BY id`],
  ['dw_process_definitions', `SELECT id FROM dw_process_definitions WHERE function_unit_id=${FU_ID} ORDER BY id`],
  ['rt_field_definitions', `SELECT id FROM rt_field_definitions WHERE table_id IN (SELECT DISTINCT relation_table_id FROM dw_form_table_bindings WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id=${FU_ID}) AND relation_table_id>0) ORDER BY id`],
  ['rt_table_versions', `SELECT id FROM rt_table_versions WHERE table_id IN (SELECT DISTINCT relation_table_id FROM dw_form_table_bindings WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id=${FU_ID}) AND relation_table_id>0) ORDER BY id`],
];

for (const [table, query] of tables) {
  const db = psql(query).map(Number);
  const sc = insertIds(table);
  const missing = db.filter((x) => !sc.includes(x));
  const extra = sc.filter((x) => !db.includes(x));
  log(missing.length === 0 && extra.length === 0 ? 'OK' : 'FAIL',
    `${table}: db=${db.length} script=${sc.length}${missing.length ? ` missing=${JSON.stringify(missing)}` : ''}${extra.length ? ` extra=${JSON.stringify(extra)}` : ''}`);
}

const rtTblDb = Number(psql(`SELECT count(*) FROM rt_table_definitions WHERE id IN (SELECT DISTINCT relation_table_id FROM dw_form_table_bindings WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id=${FU_ID}) AND relation_table_id>0)`)[0]);
const rtRowsDb = Number(psql(`SELECT count(*) FROM rt_table_data_rows WHERE table_id IN (SELECT DISTINCT relation_table_id FROM dw_form_table_bindings WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id=${FU_ID}) AND relation_table_id>0)`)[0]);
log(rtTblDb === countInsert('rt_table_definitions') ? 'OK' : 'FAIL', `rt_table_definitions: db=${rtTblDb} script=${countInsert('rt_table_definitions')}`);
log(rtRowsDb === countInsert('rt_table_data_rows') ? 'OK' : 'FAIL', `rt_table_data_rows: db=${rtRowsDb} script=${countInsert('rt_table_data_rows')}`);

const fu = psql(`SELECT current_version||'|'||lock_version||'|'||code FROM dw_function_units WHERE id=${FU_ID}`)[0].split('|');
log(SQL.includes(`'${fu[2]}'`) && SQL.includes(`'${fu[0]}'`) && SQL.includes(fu[1]) ? 'OK' : 'FAIL', `dw_function_units metadata version=${fu[0]} lock=${fu[1]}`);

console.log('\n=== Large blob parity ===');
for (const line of psql(`SELECT id::text FROM dw_form_definitions WHERE function_unit_id=${FU_ID} ORDER BY id`)) {
  const id = line;
  const dbObj = JSON.parse(psql(`SELECT config_json::text FROM dw_form_definitions WHERE id=${id}`)[0]);
  const formRe = new RegExp(`INSERT INTO dw_form_definitions[^;]*VALUES \\(${id},[\\s\\S]*?\\$json\\$([\\s\\S]*?)\\$json\\$::jsonb`);
  const sm = SQL.match(formRe);
  const scriptObj = sm ? JSON.parse(sm[1]) : null;
  const semantic = scriptObj && JSON.stringify(dbObj) === JSON.stringify(scriptObj);
  log(semantic ? 'OK' : 'FAIL', `form ${id} config_json semantic equal=${!!semantic}`);
}

const bpmn = psql(`SELECT bpmn_xml FROM dw_process_definitions WHERE function_unit_id=${FU_ID}`)[0];
const bpmnMd5Db = crypto.createHash('md5').update(bpmn).digest('hex');
const bpmnM = SQL.match(/INSERT INTO dw_process_definitions[^;]+VALUES \(6,5,5,'([^']+)'/);
const bpmnScript = bpmnM ? bpmnM[1] : null;
const bpmnMd5Script = bpmnScript ? crypto.createHash('md5').update(bpmnScript).digest('hex') : 'MISSING';
log(bpmnMd5Db === bpmnMd5Script ? 'OK' : 'FAIL', `bpmn_xml md5 db=${bpmnMd5Db} script=${bpmnMd5Script}`);

console.log('\n=== Intentionally excluded from script ===');
const excluded = [
  ['dw_versions', `SELECT count(*) FROM dw_versions WHERE function_unit_id=${FU_ID}`],
  ['dw_decision_definitions', `SELECT count(*) FROM dw_decision_definitions WHERE function_unit_id=${FU_ID}`],
  ['dw_form_stage_bindings', `SELECT count(*) FROM dw_form_stage_bindings WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id=${FU_ID})`],
  ['dw_foreign_keys', `SELECT count(*) FROM dw_foreign_keys WHERE field_id IN (SELECT id FROM dw_field_definitions WHERE table_id IN (SELECT id FROM dw_table_definitions WHERE function_unit_id=${FU_ID}))`],
  ['rt_view_configs', `SELECT count(*) FROM rt_view_configs WHERE binding_id IN (SELECT id FROM dw_form_table_bindings WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id=${FU_ID}))`],
  ['rt_lookup_configs', `SELECT count(*) FROM rt_lookup_configs WHERE form_id IN (SELECT id FROM dw_form_definitions WHERE function_unit_id=${FU_ID})`],
];
for (const [name, q] of excluded) {
  console.log(`  ${name}: ${psql(q)[0]} rows in DB (not exported by design)`);
}

console.log(`\n=== OVERALL: ${ok ? 'COMPLETE — all exported tables match DB' : 'GAPS FOUND'} ===`);
process.exit(ok ? 0 : 1);
