#!/usr/bin/env node
/**
 * Re-Deploy p0 / p3 dual-binding FUs so Admin catalog FORM snapshots freeze
 * tableBindings (and portable fkFillSources when declared).
 *
 * Does not touch Demo attachment. Does not claim Long-column immutable packs.
 */
import { execFileSync } from 'node:child_process'
import { writeFileSync, unlinkSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const TARGETS = [
  { code: 'p0-dual-binding-test', name: 'P0 Dual Binding Test', fileTable: 'p0_dual_file' },
  { code: 'p3-mi-dual-binding-test', name: 'P3 MI Dual Binding Test', fileTable: 'p3_mi_file' },
]
const CONTAINER = process.env.POSTGRES_CONTAINER ?? 'platform-postgres-dev'
const PG_USER = process.env.POSTGRES_USER ?? 'platform_dev'
const PG_DB = process.env.POSTGRES_DB ?? 'workflow_platform_dev'

function unwrap(res, body) {
  if (!res.ok()) {
    throw new Error(`HTTP ${res.status()} ${JSON.stringify(body).slice(0, 800)}`)
  }
  return body.data ?? body
}

async function unwrapRes(res) {
  return unwrap(res, await res.json().catch(() => ({})))
}

async function resolveFuId(page, headers, target) {
  const listed = await unwrapRes(
    await page.request.get(`${ORIGIN}/api/v1/function-units`, {
      headers,
      params: { name: target.name, page: 0, size: 20 },
    }),
  )
  const rows = listed.content ?? listed.records ?? (Array.isArray(listed) ? listed : [])
  const hit = (Array.isArray(rows) ? rows : []).find((row) => row.code === target.code)
  if (!hit?.id) {
    throw new Error(`DW list did not contain ${target.code}`)
  }
  return String(hit.id)
}

async function declareNestedFileFillSources(page, headers, fuId, fileTable) {
  const tables = await unwrapRes(
    await page.request.get(`${ORIGIN}/api/v1/function-units/${fuId}/tables`, { headers }),
  )
  const tableList = Array.isArray(tables) ? tables : []
  const file = tableList.find((t) => t.tableName === fileTable)
  if (!file?.id) {
    throw new Error(`table ${fileTable} missing on FU ${fuId}`)
  }
  const fields = Array.isArray(file.fieldDefinitions) ? file.fieldDefinitions : []
  const caseField = fields.find((f) => f.fieldName === 'case_id')
  const partyField = fields.find((f) => f.fieldName === 'party_id')
  if (!caseField?.id || !partyField?.id) {
    throw new Error(`${fileTable} is missing case_id / party_id field ids`)
  }
  const forms = await unwrapRes(
    await page.request.get(`${ORIGIN}/api/v1/function-units/${fuId}/forms`, { headers }),
  )
  const formList = Array.isArray(forms) ? forms : []
  let updated = 0
  for (const form of formList) {
    const bindings = await unwrapRes(
      await page.request.get(
        `${ORIGIN}/api/v1/function-units/${fuId}/forms/${form.id}/bindings`,
        { headers },
      ),
    )
    const bindingList = Array.isArray(bindings) ? bindings : []
    for (const binding of bindingList) {
      if (binding.bindingType !== 'SUB' || binding.tableName !== fileTable) continue
      if (binding.foreignKeyField !== 'party_id') continue
      await unwrapRes(
        await page.request.put(
          `${ORIGIN}/api/v1/function-units/${fuId}/forms/${form.id}/bindings/${binding.id}`,
          {
            headers,
            data: {
              tableId: binding.tableId,
              bindingType: binding.bindingType,
              bindingMode: binding.bindingMode,
              foreignKeyField: binding.foreignKeyField,
              bindingLinkMode: binding.bindingLinkMode ?? 'structuralFk',
              filterFkFieldId: binding.filterFkFieldId,
              sortOrder: binding.sortOrder,
              ...(binding.subMode ? { subMode: binding.subMode } : {}),
              fkFillSources: [
                { fieldId: caseField.id, fieldName: 'case_id', kind: 'PRIMARY' },
                { fieldId: partyField.id, fieldName: 'party_id', kind: 'PARENT' },
              ],
            },
          },
        ),
      )
      updated += 1
    }
  }
  if (updated < 1) {
    throw new Error(`no ${fileTable} party_id SUB binding updated on FU ${fuId}`)
  }
  console.log(`[fill] FU ${fuId} ${fileTable} party_id bindings=${updated}`)
}

async function deployFromDw(page, headers, fuId, changeLog) {
  const started = await unwrapRes(
    await page.request.post(`${ORIGIN}/api/v1/function-units/${fuId}/deploy`, {
      headers,
      data: { autoEnable: true, changeLog },
      timeout: 120000,
    }),
  )
  console.log(`[deploy] started id=${started.deploymentId} status=${started.status}`)
  if (started.status === 'SUCCESS') return started
  if (started.status === 'FAILED') {
    throw new Error(`deploy failed immediately: ${started.message}`)
  }
  const deadline = Date.now() + 180000
  while (Date.now() < deadline) {
    await page.waitForTimeout(2000)
    const st = await unwrapRes(
      await page.request.get(
        `${ORIGIN}/api/v1/function-units/deployments/${started.deploymentId}/status`,
        { headers },
      ),
    )
    console.log(`[deploy] ${st.status} progress=${st.progress ?? '?'} ${st.message ?? ''}`)
    if (st.status === 'SUCCESS') return st
    if (st.status === 'FAILED') {
      const steps = Array.isArray(st.steps)
        ? st.steps.map((s) => `${s.name}:${s.status}:${s.message}`).join(' | ')
        : ''
      throw new Error(`deploy failed: ${st.message || ''} ${steps}`.trim())
    }
  }
  throw new Error('deploy timed out after 180s')
}

function classifyCatalog() {
  const sql = `SELECT fu.code, fu.version, fu.enabled, fu.is_active, c.content_name,
  CASE
    WHEN c.content_data IS NULL THEN 'null'
    WHEN position('"tableBindings"' in c.content_data) > 0 THEN 'frozen_bindings'
    WHEN position('"formType"' in c.content_data) > 0 THEN 'full_form_no_bindings_key'
    WHEN left(ltrim(c.content_data), 1) = '{' THEN 'legacy_json'
    ELSE 'other'
  END AS shape,
  CASE WHEN position('"fkFillSources"' in c.content_data) > 0 THEN 'yes' ELSE 'no' END AS has_fill
FROM sys_function_units fu
JOIN sys_function_unit_contents c ON c.function_unit_id = fu.id
WHERE fu.code IN ('p0-dual-binding-test', 'p3-mi-dual-binding-test')
  AND c.content_type = 'FORM'
ORDER BY fu.code, fu.version, c.content_name;
`
  const local = join(__dirname, '_tmp-catalog-form-freeze-check.sql')
  writeFileSync(local, sql, 'utf8')
  try {
    execFileSync('docker', ['cp', local, `${CONTAINER}:/tmp/catalog-form-freeze-check.sql`], {
      stdio: 'inherit',
    })
    const out = execFileSync(
      'docker',
      [
        'exec',
        CONTAINER,
        'psql',
        '-U',
        PG_USER,
        '-d',
        PG_DB,
        '-A',
        '-t',
        '-F',
        '|',
        '-f',
        '/tmp/catalog-form-freeze-check.sql',
      ],
      { encoding: 'utf8' },
    )
    console.log(out)
    return out
  } finally {
    try {
      unlinkSync(local)
    } catch {
      /* ignore */
    }
  }
}

function assertFrozen(out) {
  const rows = out
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line.includes('dual-binding-test'))
    .map((line) => {
      const [code, version, enabled, isActive, contentName, shape, hasFill] = line.split('|')
      return { code, version, enabled, isActive, contentName, shape, hasFill }
    })
  const required = ['p0-dual-binding-test', 'p3-mi-dual-binding-test']
  for (const code of required) {
    const live = rows.filter((r) => r.code === code && r.enabled === 't' && r.isActive === 't')
    if (live.length < 1) {
      throw new Error(`no enabled+active catalog FORM rows for ${code}:\n${out}`)
    }
    const bad = live.filter((r) => r.shape !== 'frozen_bindings' || r.hasFill !== 'yes')
    if (bad.length > 0) {
      throw new Error(
        `${code} live FORM snapshots are not frozen with fkFillSources:\n${JSON.stringify(bad)}`,
      )
    }
  }
  console.log(`[ok] ${rows.filter((r) => r.enabled === 't' && r.isActive === 't').length} live FORM rows frozen with fkFillSources`)
}

async function main() {
  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage()
  try {
    const { userId } = await loginViaDwPassword(page)
    const headers = { 'X-User-Id': String(userId), 'Accept-Language': 'en' }
    for (const target of TARGETS) {
      const fuId = await resolveFuId(page, headers, target)
      console.log(`[fu] ${target.code} id=${fuId}`)
      await declareNestedFileFillSources(page, headers, fuId, target.fileTable)
      await deployFromDw(page, headers, fuId, 'Freeze catalog FORM tableBindings + fkFillSources')
    }
  } finally {
    await browser.close()
  }
  const out = classifyCatalog()
  assertFrozen(out)
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
