#!/usr/bin/env node
/**
 * Prove dual-binding + fill sources survive DW export → import into a *new* FU
 * (same DB, unique table names to avoid uk_dw_table_name), then Deploy freeze.
 *
 * Does not overwrite p0-dual-binding-test. Does not touch Demo attachment.
 */
import { execFileSync } from 'node:child_process'
import { mkdtempSync, writeFileSync, unlinkSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { chromium } from 'playwright'
import { loginViaDwPassword } from './playwright-login.mjs'

const __dirname = dirname(fileURLToPath(import.meta.url))
const ORIGIN = process.env.ORIGIN ?? 'http://localhost:3000'
const SRC_CODE = 'p0-dual-binding-test'
const SRC_NAME = 'P0 Dual Binding Test'
const FILE_TABLE = 'p0_dual_file'
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

function pythonBin() {
  for (const bin of ['python', 'py']) {
    try {
      execFileSync(bin, ['-c', 'import zipfile'], { stdio: 'ignore' })
      return bin
    } catch {
      /* try next */
    }
  }
  throw new Error('python is required to rewrite the export ZIP')
}

function rewriteZip(srcZip, destZip, replacements) {
  const py = `
import json, sys, zipfile
src, dest = sys.argv[1], sys.argv[2]
repl = json.loads(sys.argv[3])
with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dest, "w", zipfile.ZIP_DEFLATED) as zout:
    for info in zin.infolist():
        data = zin.read(info.filename)
        name = info.filename
        if not info.is_dir():
            text = data.decode("utf-8")
            for old, new in repl:
                text = text.replace(old, new)
            data = text.encode("utf-8")
        zout.writestr(info, data)
`
  execFileSync(pythonBin(), ['-c', py, srcZip, destZip, JSON.stringify(replacements)], {
    stdio: 'inherit',
  })
}

function inspectZip(zipPath) {
  const py = `
import json, sys, zipfile
out = {"forms": [], "hasFilterFkFieldId": False}
with zipfile.ZipFile(sys.argv[1]) as z:
    for name in z.namelist():
        if not name.startswith("forms/") or not name.endswith(".json"):
            continue
        form = json.loads(z.read(name))
        bindings = []
        for b in form.get("tableBindings") or []:
            fill = b.get("fkFillSources") or []
            if "filterFkFieldId" in b:
                out["hasFilterFkFieldId"] = True
            if any("fieldId" in (s or {}) for s in fill):
                out["hasFilterFkFieldId"] = True
            bindings.append({
                "tableName": b.get("tableName"),
                "bindingType": b.get("bindingType"),
                "filterFkFieldName": b.get("filterFkFieldName"),
                "kinds": [s.get("kind") for s in fill if s.get("kind")],
            })
        out["forms"].append({"file": name, "bindings": bindings})
print(json.dumps(out))
`
  return JSON.parse(execFileSync(pythonBin(), ['-c', py, zipPath], { encoding: 'utf8' }))
}

function assertPortableExport(inspected) {
  if (inspected.hasFilterFkFieldId) {
    throw new Error('export ZIP still carries environment-local field/filter ids')
  }
  const fileSubs = []
  for (const form of inspected.forms) {
    for (const binding of form.bindings) {
      if (binding.bindingType === 'SUB' && binding.tableName === FILE_TABLE) {
        fileSubs.push(binding)
      }
    }
  }
  if (fileSubs.length !== 2) {
    throw new Error(`expected 2 SUB bindings on ${FILE_TABLE} in ZIP, got ${fileSubs.length}`)
  }
  const filters = new Set(fileSubs.map((b) => b.filterFkFieldName))
  if (!filters.has('case_id') || !filters.has('party_id')) {
    throw new Error(`ZIP file SUBs missing case_id/party_id filters: ${JSON.stringify(fileSubs)}`)
  }
  const kinds = new Set(fileSubs.flatMap((b) => b.kinds))
  if (!kinds.has('PRIMARY') || !kinds.has('PARENT')) {
    throw new Error(`ZIP file SUBs missing PRIMARY/PARENT fill sources: ${JSON.stringify(fileSubs)}`)
  }
  console.log('[ok] export ZIP is portable (names + PRIMARY/PARENT, no field ids)')
}

async function resolveFuId(page, headers, name, code) {
  const listed = await unwrapRes(
    await page.request.get(`${ORIGIN}/api/v1/function-units`, {
      headers,
      params: { name, page: 0, size: 20 },
    }),
  )
  const rows = listed.content ?? listed.records ?? (Array.isArray(listed) ? listed : [])
  const hit = (Array.isArray(rows) ? rows : []).find((row) => row.code === code)
  if (!hit?.id) {
    throw new Error(`DW list did not contain ${code}`)
  }
  return String(hit.id)
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

function assertCatalogFrozen(code) {
  const sql = `SELECT fu.code, fu.version, fu.enabled,
  CASE WHEN position('"tableBindings"' in c.content_data) > 0 THEN 'frozen_bindings' ELSE 'other' END AS shape,
  CASE WHEN position('"fkFillSources"' in c.content_data) > 0 THEN 'yes' ELSE 'no' END AS has_fill
FROM sys_function_units fu
JOIN sys_function_unit_contents c ON c.function_unit_id = fu.id
WHERE fu.code = '${code.replace(/'/g, "''")}'
  AND c.content_type = 'FORM'
  AND fu.enabled = true
  AND fu.is_active = true;
`
  const local = join(__dirname, '_tmp-import-portability-check.sql')
  writeFileSync(local, sql, 'utf8')
  try {
    execFileSync('docker', ['cp', local, `${CONTAINER}:/tmp/import-portability-check.sql`], {
      stdio: 'inherit',
    })
    const out = execFileSync(
      'docker',
      ['exec', CONTAINER, 'psql', '-U', PG_USER, '-d', PG_DB, '-A', '-t', '-F', '|',
        '-f', '/tmp/import-portability-check.sql'],
      { encoding: 'utf8' },
    )
    console.log(out)
    const live = out.split(/\r?\n/).map((l) => l.trim()).filter((l) => l.includes(code))
    if (live.length < 1) {
      throw new Error(`no enabled catalog FORM rows for imported ${code}`)
    }
    const bad = live.filter((l) => !l.includes('frozen_bindings') || !l.endsWith('|yes'))
    if (bad.length > 0) {
      throw new Error(`imported catalog FORM not frozen with fill sources:\n${bad.join('\n')}`)
    }
  } finally {
    try {
      unlinkSync(local)
    } catch {
      /* ignore */
    }
  }
}

async function assertImportedBindings(page, headers, fuId, fileTable) {
  const tables = await unwrapRes(
    await page.request.get(`${ORIGIN}/api/v1/function-units/${fuId}/tables`, { headers }),
  )
  const file = (Array.isArray(tables) ? tables : []).find((t) => t.tableName === fileTable)
  if (!file?.id) {
    throw new Error(`imported FU missing table ${fileTable}`)
  }
  const fields = Array.isArray(file.fieldDefinitions) ? file.fieldDefinitions : []
  const caseId = fields.find((f) => f.fieldName === 'case_id')?.id
  const partyId = fields.find((f) => f.fieldName === 'party_id')?.id
  if (!caseId || !partyId) {
    throw new Error(`${fileTable} missing case_id/party_id after import`)
  }
  const forms = await unwrapRes(
    await page.request.get(`${ORIGIN}/api/v1/function-units/${fuId}/forms`, { headers }),
  )
  const formList = Array.isArray(forms) ? forms : []
  const fileSubs = []
  for (const form of formList) {
    const bindings = await unwrapRes(
      await page.request.get(
        `${ORIGIN}/api/v1/function-units/${fuId}/forms/${form.id}/bindings`,
        { headers },
      ),
    )
    for (const binding of Array.isArray(bindings) ? bindings : []) {
      if (binding.bindingType === 'SUB' && binding.tableName === fileTable) {
        fileSubs.push(binding)
      }
    }
  }
  if (fileSubs.length !== 2) {
    throw new Error(`imported FU expected 2 SUB on ${fileTable}, got ${fileSubs.length}`)
  }
  const filters = new Set(fileSubs.map((b) => String(b.filterFkFieldId)))
  if (!filters.has(String(caseId)) || !filters.has(String(partyId))) {
    throw new Error(`imported filter FK ids were not remapped: ${JSON.stringify(fileSubs)}`)
  }
  const kinds = new Set(
    fileSubs.flatMap((b) => (b.fkFillSources ?? []).map((s) => s.kind)),
  )
  if (!kinds.has('PRIMARY') || !kinds.has('PARENT')) {
    throw new Error(`imported fill sources missing PRIMARY/PARENT: ${JSON.stringify(fileSubs)}`)
  }
  console.log(`[ok] imported bindings filter=${[...filters].join(',')} fill=${[...kinds].join(',')}`)
}

async function main() {
  const stamp = Date.now().toString(36).slice(-6)
  const newName = `P0 Dual Binding Import ${stamp}`
  const newCode = `p0-imp-${stamp}`
  const tableMap = [
    ['p0_dual_file', `p0i_${stamp}_file`],
    ['p0_dual_party', `p0i_${stamp}_party`],
    ['p0_dual_case', `p0i_${stamp}_case`],
  ]
  const tmp = mkdtempSync(join(tmpdir(), 'fu-import-'))
  const srcZip = join(tmp, 'src.zip')
  const destZip = join(tmp, 'dest.zip')

  const browser = await chromium.launch({ headless: true })
  const page = await browser.newPage()
  try {
    const { userId } = await loginViaDwPassword(page)
    const headers = { 'X-User-Id': String(userId), 'Accept-Language': 'en' }
    const srcId = await resolveFuId(page, headers, SRC_NAME, SRC_CODE)
    const exportRes = await page.request.get(`${ORIGIN}/api/v1/function-units/${srcId}/export`, {
      headers,
    })
    if (!exportRes.ok()) {
      throw new Error(`export failed HTTP ${exportRes.status()}`)
    }
    writeFileSync(srcZip, Buffer.from(await exportRes.body()))
    assertPortableExport(inspectZip(srcZip))

    rewriteZip(srcZip, destZip, [
      [SRC_NAME, newName],
      [SRC_CODE, newCode],
      ...tableMap,
    ])
    const imported = await unwrapRes(
      await page.request.post(`${ORIGIN}/api/v1/export-import/import`, {
        headers,
        multipart: {
          file: {
            name: `${newCode}.zip`,
            mimeType: 'application/zip',
            buffer: readFileSync(destZip),
          },
        },
        params: { changeLog: 'P0 dual-binding import portability' },
        timeout: 120000,
      }),
    )
    if (imported.status !== 'SUCCESS' || !imported.functionUnitId) {
      throw new Error(`import failed: ${JSON.stringify(imported).slice(0, 800)}`)
    }
    if (imported.versioned === true) {
      throw new Error('import overwrote an existing FU; expected a new unit')
    }
    const newId = String(imported.functionUnitId)
    console.log(`[import] id=${newId} code=${imported.name ?? newName}`)
    await assertImportedBindings(page, headers, newId, tableMap[0][1])
    await deployFromDw(page, headers, newId, 'Deploy imported dual-binding FU')
    assertCatalogFrozen(newCode)
    console.log(`[ok] imported ${newCode} (id=${newId}) kept dual SUB + fill sources through Deploy`)
  } finally {
    await browser.close()
  }
}

main().catch((err) => {
  console.error(err)
  process.exit(1)
})
