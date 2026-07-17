import { describe, it, expect } from 'vitest'
import {
  buildInitialRow,
  applyAuditFieldDefaults,
  mergeFormRowWithSeed,
  buildRules,
  type DialogColumn,
} from '../subTableAddDialogHelpers'

// Regression for fast-check Property 3 counterexample [{"field":"__proto__","type":"text"}]:
// designer-provided field names that collide with Object.prototype members must become
// own enumerable keys (write side) and must not resolve inherited members (read side).
describe('rowInit prototype-colliding field names', () => {
  it('buildInitialRow creates own keys for __proto__/constructor/prototype', () => {
    const columns: DialogColumn[] = [
      { field: '__proto__', label: 'p', type: 'text' },
      { field: 'constructor', label: 'c', type: 'number' },
      { field: 'prototype', label: 'pt', type: 'checkbox' },
    ]
    const row = buildInitialRow(columns)
    expect(Object.keys(row)).toEqual(['__proto__', 'constructor', 'prototype'])
    expect(row['__proto__']).toBe('')
    expect(Object.getPrototypeOf(row)).toBe(Object.prototype)
  })

  it('applyAuditFieldDefaults handles a __proto__ column without polluting', () => {
    const row: Record<string, unknown> = {}
    applyAuditFieldDefaults(row, [{ field: '__proto__', label: 'p', type: 'text' }])
    expect(Object.getPrototypeOf(row)).toBe(Object.prototype)
  })

  it('mergeFormRowWithSeed keeps __proto__ seed values as own keys', () => {
    const seed = JSON.parse('{"__proto__": "seeded"}') as Record<string, unknown>
    const merged = mergeFormRowWithSeed(seed, {})
    expect(Object.keys(merged)).toContain('__proto__')
    expect(Object.getPrototypeOf(merged)).toBe(Object.prototype)
  })

  it('buildRules does not mutate the rules prototype for a required __proto__ field', () => {
    const rules = buildRules([{ field: '__proto__', label: 'p', type: 'text', required: true }])
    expect(Object.keys(rules)).toContain('__proto__')
    expect(Object.getPrototypeOf(rules)).toBe(Object.prototype)
  })
})
