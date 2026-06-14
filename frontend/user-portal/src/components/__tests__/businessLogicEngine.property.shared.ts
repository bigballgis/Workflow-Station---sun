import * as fc from 'fast-check'

// ─── Shared Arbitraries ─────────────────────────────────────────────────────

export const fieldNameArb = fc.string({ minLength: 1, maxLength: 20 }).filter((s) => /^[a-zA-Z]\w*$/.test(s))

export const conditionOperatorArb = fc.constantFrom(
  'equals' as const,
  'not-equals' as const,
  'contains' as const,
  'greater-than' as const,
  'less-than' as const,
  'is-empty' as const,
  'is-not-empty' as const,
)

export const scalarValueArb = fc.oneof(
  fc.string({ minLength: 0, maxLength: 20 }),
  fc.integer({ min: -1000, max: 1000 }),
  fc.double({ min: -1000, max: 1000, noNaN: true, noDefaultInfinity: true }),
  fc.boolean(),
  fc.constant(null),
  fc.constant(undefined),
  fc.constant(''),
)

export const aggregationArb = fc.constantFrom('SUM' as const, 'AVG' as const, 'COUNT' as const, 'MIN' as const, 'MAX' as const)
