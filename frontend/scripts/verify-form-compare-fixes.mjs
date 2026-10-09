#!/usr/bin/env node
// Local QA only. Auth remains in memory; historical snapshots are never edited.
import assert from 'node:assert/strict'
import { formCompareClient } from './form-compare-fix-client.mjs'

const phase = process.argv[2]
assert.ok(['before', 'after'].includes(phase))
const { compare } = await formCompareClient()
const cases = [['form-rename', 244, 245], ['top-reorder', 269, 270],
  ['nested-reorder', 270, 271], ['api-field-rename', 271, 272],
  ['ui-field-rename', 289, 290], ['binding-delete', 296, 297]]
for (const [label, a, b] of cases) {
  const result = await compare(50049, a, b, `${phase}-${label}`)
  const f = result.modules.find(m => m.key === 'FORMS').semantic
  if (phase === 'after') {
    assert.equal(f.counts.added, 0, label)
    if (label === 'binding-delete') {
      assert.deepEqual(f.counts, { added: 0, modified: 0, removed: 1 })
      assert.equal(f.items[0].objectType, 'FORM_BINDING')
      assert.equal(f.scope, 'PARTIAL')
    } else {
      assert.equal(f.counts.removed, 0, label); assert.ok(f.counts.modified > 0, label)
      const field = label.includes('reorder') ? 'position' : label === 'form-rename' ? 'formName' : 'field'
      assert.ok(f.items.some(i => i.fields.some(v => v.field === field)), label)
    }
  }
  process.stdout.write(`${JSON.stringify({ phase, label, counts: f.counts, scope: f.scope })}\n`)
}
