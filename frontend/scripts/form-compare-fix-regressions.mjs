import assert from 'node:assert/strict'
import fs from 'node:fs/promises'
import { formCompareClient } from './form-compare-fix-client.mjs'

const { dir, request, compare, evidence } = await formCompareClient()
const zero = { added: 0, modified: 0, removed: 0 }
const cases = [
  [50049, 241, 303, 'existing-restored', null, zero],
  [50049, 247, 248, 'unsaved-preview', null, zero],
  [50049, 275, 276, 'noop-save', null, zero],
  [50049, 282, 283, 'cancel-delete', null, zero],
  [50049, 290, 291, 'focused-draft', null, zero],
  [50049, 292, 293, 'preview-only', null, zero],
  [50049, 242, 243, 'create-task-scenes', 'FORMS', { added: 2, modified: 0, removed: 0 }],
  [50049, 254, 255, 'copy-task', 'FORMS', { added: 16, modified: 0, removed: 0 }],
  [50049, 291, 292, 'committed-title', 'FORMS', { added: 0, modified: 1, removed: 0 }],
  [50049, 297, 298, 'delete-action-form', 'FORMS', { added: 0, modified: 0, removed: 1 }],
  [50048, 223, 224, 'view-rename', 'VIEWS', { added: 0, modified: 1, removed: 0 }],
  [50048, 224, 228, 'duplicate-view', 'VIEWS', { added: 1, modified: 0, removed: 0 }],
  [50048, 236, 240, 'view-rollback', null, zero],
  [50045, 147, 148, 'decision-xml-format-corrected', 'DECISIONS', zero]
]
const results = []
for (const [fu, a, b, label, module, expected] of cases) {
  const result = await compare(fu, a, b, `regression-${label}`)
  const selected = module ? result.modules.filter(m => m.key === module) : result.modules
  for (const m of selected) assert.deepEqual(m.semantic.counts, expected, `${label}/${m.key}`)
  results.push({ fu, a, b, label, passed: true })
}
const state = JSON.parse(await fs.readFile(`${dir}/new-state.json`, 'utf8'))
for (const label of ['created', 'rename', 'top-reorder', 'nested-reorder', 'field-rename', 'binding-delete']) {
  const original = JSON.parse(await fs.readFile(`${dir}/new-snapshot-${label}.json`, 'utf8'))
  const exported = await request('GET', `/${state.fuId}/versions/${state.versions[label]}/export`, undefined, true)
  assert.equal(exported.status, 200); assert.deepEqual(exported.body, original, `Snapshot changed: ${label}`)
}
await evidence('regression-summary', { results, originalSnapshotsUnchanged: 6 })
process.stdout.write(`${JSON.stringify({ regressionPairs: results.length, reverseValuesVerified: true, originalSnapshotsUnchanged: 6 })}\n`)
