/** Read-only paired fixture check; no engine variable mutation or business request writes. */
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { chromium } from 'playwright'
import { loginViaPortalPassword } from './playwright-login.mjs'

const code = process.env.FIXTURE_PROCESS_KEY
assert.match(code || '', /^batch-verification-[a-z0-9-]{1,90}$/)
const reserved = ['__subTableBatchRuntime__', '__subTableBatchDeletedRows__']
const readStore = () => JSON.parse(execFileSync('docker', ['exec', 'platform-postgres-dev', 'psql', '-U', 'platform_dev',
  '-d', 'workflow_platform_dev', '-Atc', `SELECT json_agg(json_build_object('id',id,'runtime',variables->'__subTableBatchRuntime__',
  'deleted',variables->'__subTableBatchDeletedRows__') ORDER BY id) FROM up_process_instance WHERE process_definition_key='${code}'`],
{ encoding: 'utf8' }).trim())
const before = readStore()
assert.equal(before.length, 2, 'Use the paired, uniquely cloned enabled/disabled fixture only')
assert.equal(before.filter(row => row.runtime).length, 1)
const browser = await chromium.launch({ headless: true })
try {
  const page = await browser.newPage()
  await loginViaPortalPassword(page)
  for (const row of before) {
    const response = await page.request.get(`http://localhost:3000/api/portal/processes/${row.id}`)
    assert.equal(response.status(), 200)
    const text = await response.text()
    const body = JSON.parse(text)
    assert.equal(body.success, true)
    assert.equal(body.data.processDefinitionKey, code)
    assert.match(body.data.processDefinitionName, /^Batch verification /)
    for (const key of reserved) assert.equal(text.includes(key), false, 'Local metadata must not enter process DTO')
  }
  assert.deepEqual(readStore(), before, 'Application detail must neither replace nor invent local batch metadata')
  console.log(JSON.stringify({ pairedInstances: before.length, enhanced: 1, legacy: 1, metadataUnchanged: true, responsePrivate: true }))
} finally { await browser.close() }
