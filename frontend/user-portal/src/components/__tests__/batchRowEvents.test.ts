import { describe, expect, it } from 'vitest'
import { reactive } from 'vue'
import { createBatchRowEventRunner } from '@platform-shared/batch/rowEvents'
import { createPortalFormApi, parseFormCreateEventHandler } from '@/utils/formCreateEventRuntime'
import { batchPersonIdentity } from '@platform-shared/batch/personIdentity'

const run = createBatchRowEventRunner(parseFormCreateEventHandler, createPortalFormApi)
const rules = [{ type: 'miAssignment', children: [
  { type: 'lookup', field: 'handler', on: { change: '$FNX:api.setValue("email", value.email + api.getValue("suffix"));' } },
  { type: 'input', field: 'email' }, { type: 'input', field: 'suffix' },
] }]

describe('Batch row change events', () => {
  it('keeps Lookup objects and evaluates against each selected row independently', () => {
    const person = { id: 'one', email: 'test@example.invalid' }
    const rows = reactive([{ handler: person, email: 'old-a', suffix: '-a' },
      { handler: person, email: 'old-b', suffix: '-b' }, { handler: person, email: 'untouched', suffix: '-c' }])
    const next = rows.map((row, i) => i < 2 ? run(row, rules, ['handler'], () => false) : row)
    expect(next.map(row => row.email)).toEqual(['test@example.invalid-a', 'test@example.invalid-b', 'untouched'])
    expect(rows.map(row => row.email)).toEqual(['old-a', 'old-b', 'untouched'])
    expect(next[0]!.handler).toEqual(person)
    expect(batchPersonIdentity(person)).toBe('one')
    expect(() => batchPersonIdentity([person])).toThrow()
  })

  it('rejects event writes to readonly and undeclared fields atomically', () => {
    const row = { handler: { id: 'one', email: 'test@example.invalid' }, email: 'keep', suffix: '' }
    expect(() => run(row, rules, ['handler'], field => field === 'email')).toThrow('batchEventWriteDenied')
    expect(row.email).toBe('keep')
    const unknown = [{ field: 'handler', on: { change: '$FNX:api.setValue("other", "bad")' } }]
    expect(() => run(row, unknown, ['handler'], () => false)).toThrow('batchEventWriteDenied')
    const unsupported = [{ field: 'handler', on: { change: '$FNX:api.hidden(true, "email")' } }]
    expect(() => run(row, unsupported, ['handler'], () => false)).toThrow('batchEventApiUnsupported')
  })
})
