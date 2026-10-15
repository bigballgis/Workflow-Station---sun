/** Windows dev-environment negative test: both Admin import APIs must reject a bad synthetic ZIP. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { chromium } from 'playwright'
import { loginViaAdminPassword } from './playwright-login.mjs'

const code = `batch-invalid-schema-${Date.now().toString(36)}`
const files = {
  'manifest.json': { code, name: `Batch verification ${code}`, version: '1.0.0' },
  'forms/form_0.json': { formId: 1, formName: 'Synthetic invalid batch', configJson: {
    rule: [], batchForms: { '101': { schemaVersion: 1.5, rule: [{ type: 'input', field: 'value' }] } },
  } },
}
// Use the built-in ZIP implementation in memory; no new archive dependency or filesystem writes.
const archive = execFileSync('powershell', ['-NoProfile', '-Command', `
Add-Type -AssemblyName System.IO.Compression
$files = [Console]::In.ReadToEnd() | ConvertFrom-Json
$buffer = [IO.MemoryStream]::new()
$zip = [IO.Compression.ZipArchive]::new($buffer, [IO.Compression.ZipArchiveMode]::Create, $true)
foreach ($file in $files.PSObject.Properties) {
  $entry = $zip.CreateEntry($file.Name)
  $writer = [IO.StreamWriter]::new($entry.Open(), [Text.UTF8Encoding]::new($false))
  $writer.Write(($file.Value | ConvertTo-Json -Depth 30 -Compress))
  $writer.Dispose()
}
$zip.Dispose()
[Console]::Write([Convert]::ToBase64String($buffer.ToArray()))
$buffer.Dispose()
`], { input: JSON.stringify(files), encoding: 'utf8' }).trim()
const origin = 'http://localhost:3000'
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage()
  await loginViaAdminPassword(page)
  const before = await page.request.get(`${origin}/api/v1/admin/function-units/code/${code}/versions`)
  assert.equal(before.ok(), true)
  assert.deepEqual(await before.json(), [])
  const responses = [
    await page.request.post(`${origin}/api/v1/admin/function-units/import`, {
      data: { fileName: `${code}.zip`, fileContent: archive, overwrite: false },
    }),
    await page.request.post(`${origin}/api/v1/admin/function-units-import/import`, {
      multipart: { file: { name: `${code}.zip`, mimeType: 'application/zip', buffer: Buffer.from(archive, 'base64') } },
    }),
  ]
  for (const response of responses) {
    assert.equal(response.status(), 400, 'Invalid batch ZIP must fail as a package validation error')
    const body = await response.json()
    assert.match(JSON.stringify(body), /batch/i, 'Rejection must identify the bad batch contract, not auth or infrastructure')
  }
  const after = await page.request.get(`${origin}/api/v1/admin/function-units/code/${code}/versions`)
  assert.equal(after.ok(), true)
  assert.deepEqual(await after.json(), [], 'Neither import may leave a catalog version behind')
  console.log(`PASS: both Admin import APIs reject fractional batch schema; no catalog version created (${code})`)
} finally { await browser.close() }
