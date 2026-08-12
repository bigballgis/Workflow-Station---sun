/**
 * Golden vector contract test — TypeScript side.
 *
 * The Java interpreter runs the SAME frontend/shared/src/computedField/goldenVectors.json in
 * ComputedFieldGoldenVectorTest. A case may only be added when both sides pass it; that is the
 * whole mechanism preventing the client preview and the authoritative server recalculation from
 * drifting apart.
 */
import { describe, expect, it } from 'vitest'
import vectors from '@platform-shared/computedField/goldenVectors.json'
import {
  collectDependencies,
  evaluateAst,
  normalizeSubTables,
  parseFormula,
  toDecimalString,
  type EvaluationContext,
  type SliceIdentity,
} from '@platform-shared/computedField'

interface BaseCase {
  name: string
  formula: string
  expect?: string | boolean
  expectBlank?: boolean
  expectError?: string
}

interface EvalCase extends BaseCase {
  row: Record<string, unknown>
  subTables?: Record<string, Array<Record<string, unknown>>>
}

interface AliasCase extends BaseCase {
  rawSubTables: Record<string, unknown>
  sliceIdentities: Record<string, SliceIdentity>
  expectedCanonicalKeys: string[]
}

const suite = vectors as unknown as {
  divisionScale: number
  cases: EvalCase[]
  syntaxErrorCases: BaseCase[]
  aliasDeduplicationCases: AliasCase[]
}

/** Evaluate a vector case and reduce the outcome to a comparable primitive. */
function run(formula: string, context: EvaluationContext) {
  const parsed = parseFormula(formula)
  if (!parsed.ok) return { errorCode: parsed.error.code }
  const evaluated = evaluateAst(parsed.ast, context)
  if (!evaluated.ok) return { errorCode: evaluated.error.code }
  const value = evaluated.value
  switch (value.kind) {
    case 'number': return { actual: toDecimalString(value.value) }
    case 'text': return { actual: value.value }
    case 'boolean': return { actual: value.value }
    default: return { blank: true }
  }
}

function assertOutcome(expected: BaseCase, outcome: ReturnType<typeof run>) {
  if (expected.expectError) {
    expect(outcome.errorCode, `expected ${expected.expectError}`).toBe(expected.expectError)
    return
  }
  expect(outcome.errorCode, 'unexpected evaluation error').toBeUndefined()
  if (expected.expectBlank) {
    expect(outcome.blank, 'expected a blank result').toBe(true)
    return
  }
  expect(outcome.actual).toBe(expected.expect)
}

describe('computed field golden vectors', () => {
  it('pins the division scale shared with the Java interpreter', () => {
    expect(suite.divisionScale).toBe(10)
  })

  it.each(suite.cases.map((c) => [c.name, c] as const))('%s', (_name, testCase) => {
    assertOutcome(testCase, run(testCase.formula, {
      row: testCase.row ?? {},
      subTables: testCase.subTables,
    }))
  })

  it.each(suite.syntaxErrorCases.map((c) => [c.name, c] as const))(
    'rejects: %s',
    (_name, testCase) => {
      const parsed = parseFormula(testCase.formula)
      expect(parsed.ok).toBe(false)
      if (!parsed.ok) expect(parsed.error.code).toBe(testCase.expectError)
    },
  )

  describe('sub-table alias de-duplication', () => {
    it.each(suite.aliasDeduplicationCases.map((c) => [c.name, c] as const))(
      '%s',
      (_name, testCase) => {
        const subTables = normalizeSubTables(
          testCase.rawSubTables as Record<string, unknown>,
          (sliceKey) => testCase.sliceIdentities[sliceKey],
        )
        expect(Object.keys(subTables).sort()).toEqual(testCase.expectedCanonicalKeys)
        assertOutcome(testCase, run(testCase.formula, { row: {}, subTables }))
      },
    )
  })
})

describe('dependency derivation', () => {
  it('re-derives the same dependencies from the AST that the parser reported', () => {
    const parsed = parseFormula('ROUND(SUM(request_items.amount) * (1 + tax_rate), scale_digits)')
    expect(parsed.ok).toBe(true)
    if (!parsed.ok) return
    expect(parsed.dependsOn).toEqual(['request_items.amount', 'scale_digits', 'tax_rate'])
    // The backend trusts only this derivation, never the client-supplied list.
    expect(collectDependencies(parsed.ast)).toEqual(parsed.dependsOn)
  })
})
